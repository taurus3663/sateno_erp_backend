package com.sateno_b.www.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.LiveEventDto;
import com.sateno_b.www.model.dto.LiveSnapshotDto;
import com.sateno_b.www.model.entity.LiveAbandonedCheckoutEntity;
import com.sateno_b.www.model.entity.LiveProductStatEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.repository.LiveAbandonedCheckoutRepository;
import com.sateno_b.www.model.repository.LiveProductStatRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сърцето на Live проследяването.
 *  - Държи „живото" състояние в паметта (активни сесии/колички/каси) с изтичане по време (TTL).
 *  - Увеличава дневните статистики на продуктите в базата.
 *  - При изтекла каса с въведени данни — записва „Напусната каса" (lead) в базата.
 *  - Праща снапшот към ERP таблото през WebSocket /topic/live.
 *
 * Защо в паметта: живото състояние е краткотрайно; backend-ът е един процес.
 * Важните данни (lead-ове и статистики) се пазят в Postgres.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class LiveTrackingService {

    private final SiteRepository siteRepository;
    private final LiveProductStatRepository statRepository;
    private final LiveAbandonedCheckoutRepository abandonedRepository;
    private final LiveHistoryService historyService;                // трайна история (отделна транзакция)
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    private static final ZoneId ZONE = ZoneId.of("Europe/Sofia");
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZONE);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE);
    private static final DateTimeFormatter DATE_HHMM = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZONE);

    // Колко време сесията се счита за „жив посетител" (без ново събитие/heartbeat).
    private static final long VISITOR_TTL_SEC = 35;
    // Колко време количка/каса остава „активна" без активност.
    private static final long CART_TTL_SEC = 15 * 60;

    private final Map<String, LiveSession> sessions = new ConcurrentHashMap<>();
    private final Deque<LiveSnapshotDto.ActivityView> recentActivity = new ConcurrentLinkedDeque<>();
    private final AtomicInteger displaySeq = new AtomicInteger(0);

    // Дневни агрегати за KPI картата (в паметта; нулиране при смяна на деня).
    // Забележка: при рестарт на backend-а започват наново за деня.
    private final java.util.Set<String> todayVisitors = ConcurrentHashMap.newKeySet();
    private final AtomicInteger todayOrders = new AtomicInteger(0);
    private volatile LocalDate statsDay = LocalDate.now(ZONE);
    private volatile boolean dirty = true;
    private volatile long lastPushAt = 0;
    private static final long MIN_PUSH_INTERVAL_MS = 300;

    private enum Stage { VISITOR, CART, CHECKOUT, ORDERED }

    private static class LiveSession {
        int displayId;
        String sessionToken; // анонимен токен на клиента (satl_sid) — за дедупликация на lead-ове
        Long siteId;
        String currency;
        Stage stage = Stage.VISITOR;
        Instant lastSeen = Instant.now();
        BigDecimal cartValue;
        List<LiveEventDto.Item> items = new ArrayList<>();
        String checkoutStatus;
        String cartStatus; // „Разглежда количката" — докато клиентът е на страницата на количката
        boolean hasData;
        String name, phone, email;
        boolean checkoutCounted; // да не броим checkoutStarts повече от веднъж
        boolean abandonedPersisted; // да запишем „напусната каса" само веднъж на сесия
        final Set<Long> addedProductIds = new HashSet<>(); // продукти, вече броени като „добавяне в количка" (веднъж на продукт на сесия)
    }

    // ---------------------------------------------------------------------
    //  Прием на събитие
    // ---------------------------------------------------------------------
    @Transactional
    public void handleEvent(LiveEventDto e) {
        if (e == null || e.getType() == null || e.getSession() == null) return;

        SiteEntity site = resolveActiveSite(e.getSite());
        if (site == null) return; // непознат/неактивен сайт — игнорираме

        LiveSession s = sessions.computeIfAbsent(e.getSession(), k -> {
            LiveSession ns = new LiveSession();
            ns.displayId = displaySeq.incrementAndGet();
            return ns;
        });
        s.siteId = site.getId();
        s.sessionToken = e.getSession();
        if (e.getCurrency() != null) s.currency = e.getCurrency();
        s.lastSeen = Instant.now();

        // Дневни агрегати: уникален посетител за деня.
        rollStatsDayIfNeeded();
        todayVisitors.add(e.getSession());

        switch (e.getType()) {
            case "visitor" -> {
                // Напуснал е касата (не е на нейната страница), но още има количка → „Активни колички".
                if (!Boolean.TRUE.equals(e.getOnCheckout())) leaveCheckoutToCart(s);
                // Статус „Разглежда количката" само докато е на страницата на количката.
                if (s.stage == Stage.CART) {
                    s.cartStatus = Boolean.TRUE.equals(e.getOnCart()) ? "Разглежда количката" : null;
                }
                addActivity("visitor", "Нов посетител в сайта", null);
            }
            case "product_view" -> {
                // Разглежда продукт → не е на касата; ако е бил на каса → връща се в „Активни колички".
                leaveCheckoutToCart(s);
                if (s.stage == Stage.CART) s.cartStatus = null; // гледа продукт, не количката
                incrementStat(site.getId(), e, "views");
                addActivity("visitor", "Разглеждан продукт", e.getProductName());
            }
            case "cart_update" -> {
                // Връщане в количката (вкл. обратно от касата) → стадий CART.
                // Не пипаме само вече завършена поръчка.
                if (s.stage != Stage.ORDERED) {
                    s.stage = Stage.CART;
                    s.checkoutStatus = null;
                }
                // Статус „Разглежда количката" само когато събитието идва от страницата на количката.
                s.cartStatus = Boolean.TRUE.equals(e.getOnCart()) ? "Разглежда количката" : null;
                s.cartValue = e.getCartValue();
                if (e.getItems() != null) s.items = e.getItems();
                // Реалният тракер праща цялата количка в items[] (без productId отгоре).
                // Броим „добавяне в количка" по продукт — веднъж на продукт на сесия.
                incrementAddToCartForNewItems(site.getId(), s, e.getItems());
                addActivity("cart", "Добавен продукт в количка", e.getProductName());
            }
            case "cart_clear" -> {
                // Количката е изпразнена → клиентът вече няма активна количка/каса.
                if (s.stage == Stage.CART || s.stage == Stage.CHECKOUT) {
                    s.stage = Stage.VISITOR;
                    s.items = new ArrayList<>();
                    s.cartValue = null;
                    s.cartStatus = null;
                    s.checkoutStatus = null;
                    addActivity("cart", "Изпразнена количка", null);
                }
            }
            case "checkout_start" -> {
                s.stage = Stage.CHECKOUT;
                if (e.getCartValue() != null) s.cartValue = e.getCartValue();
                if (e.getItems() != null) s.items = e.getItems();
                s.checkoutStatus = "Разглежда касата";
                if (!s.checkoutCounted) {
                    incrementCheckoutStartsForItems(site.getId(), s);
                    s.checkoutCounted = true;
                }
                addActivity("checkout", "Започната каса", money(s.cartValue, s.currency));
            }
            case "checkout_data" -> {
                s.stage = Stage.CHECKOUT;
                s.checkoutStatus = "Въвежда данни";
                s.hasData = true;
                if (e.getName() != null) s.name = e.getName();
                if (e.getPhone() != null) s.phone = e.getPhone();
                if (e.getEmail() != null) s.email = e.getEmail();
            }
            case "order_complete" -> {
                todayOrders.incrementAndGet();
                incrementOrdersForItems(site.getId(), s, e);
                addActivity("order", "Поръчка завършена",
                        (e.getOrderId() != null ? "#" + e.getOrderId() + " – " : "") + money(s.cartValue, s.currency));
                // Ако клиентът е бил записан като „напусната каса" преди да завърши поръчката, изтриваме записа.
                if (e.getSession() != null) {
                    abandonedRepository.deleteBySiteIdAndSessionToken(site.getId(), e.getSession());
                }
                sessions.remove(e.getSession()); // завършена — не е напусната
            }
            case "leave" -> {
                // Записваме lead-а (ако има данни), но НЕ махаме сесията — клиентът още може
                // да има количка и да продължи да пазарува → остава в „Активни колички".
                persistAbandonedOnce(s);
                leaveCheckoutToCart(s);
            }
            default -> { /* непознат тип — игнор */ }
        }

        // Трайна история по сесия/клиент (за дизайна „Поведение на клиента").
        // Отделна транзакция — не влияе на „живото" състояние/статистиките горе.
        historyService.persist(site.getId(), e);

        dirty = true;
        pushThrottled();
    }

    // ---------------------------------------------------------------------
    //  Изчистване на изтекли сесии + засичане на напуснати каси
    // ---------------------------------------------------------------------
    @Scheduled(fixedRate = 15000)
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, LiveSession>> it = sessions.entrySet().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            LiveSession s = it.next().getValue();
            long idleSec = now.getEpochSecond() - s.lastSeen.getEpochSecond();
            if (idleSec > CART_TTL_SEC) {
                // изтекла каса с данни и без поръчка → напусната каса (само веднъж)
                persistAbandonedOnce(s);
                it.remove();
                changed = true;
            }
        }
        if (changed) dirty = true;
    }

    /**
     * Бута снапшота МИГНОВЕНО при ново събитие, но не по-често от MIN_PUSH_INTERVAL_MS
     * (троттъл при наплив). Каквото е троттълнато, го хваща предпазната задача по-долу.
     */
    private synchronized void pushThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastPushAt < MIN_PUSH_INTERVAL_MS) return; // твърде скоро — таймерът ще го прати
        lastPushAt = now;
        dirty = false;
        pushSnapshot();
    }

    // Предпазна мрежа: праща бързо всичко останало непратено (троттъл/cleanup).
    @Scheduled(fixedRate = 1000)
    public void pushIfDirty() {
        if (!dirty) return;
        dirty = false;
        lastPushAt = System.currentTimeMillis();
        pushSnapshot();
    }

    private void pushSnapshot() {
        try {
            messagingTemplate.convertAndSend("/topic/live", buildSnapshot());
        } catch (Exception ex) {
            log.warn("Live: грешка при изпращане на снапшот: {}", ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    //  Снапшот за таблото
    // ---------------------------------------------------------------------
    public LiveSnapshotDto buildSnapshot() {
        rollStatsDayIfNeeded();
        Instant now = Instant.now();
        int visitors = 0;
        List<LiveSnapshotDto.CartView> carts = new ArrayList<>();
        List<LiveSnapshotDto.CheckoutView> checkouts = new ArrayList<>();

        for (LiveSession s : sessions.values()) {
            long idleSec = now.getEpochSecond() - s.lastSeen.getEpochSecond();
            boolean present = idleSec <= VISITOR_TTL_SEC;
            if (present) visitors++;

            // Колички/каси се показват само докато посетителят е още на сайта (жива сесия),
            // за да са съгласувани с „Активни посетители". Сесията остава в паметта до
            // CART_TTL_SEC за засичане на напусната каса (виж cleanup()).
            if (!present) continue;

            if (s.stage == Stage.CART) {
                carts.add(new LiveSnapshotDto.CartView(
                        s.displayId, s.cartValue, s.currency, itemCount(s), HMS.format(s.lastSeen), imagesOf(s), s.cartStatus));
            } else if (s.stage == Stage.CHECKOUT) {
                checkouts.add(new LiveSnapshotDto.CheckoutView(
                        s.displayId, s.cartValue, s.currency, itemCount(s),
                        s.checkoutStatus != null ? s.checkoutStatus : "На касата", HMS.format(s.lastSeen), imagesOf(s)));
            }
        }
        carts.sort(Comparator.comparingInt(LiveSnapshotDto.CartView::getId));
        checkouts.sort(Comparator.comparingInt(LiveSnapshotDto.CheckoutView::getId));

        List<LiveSnapshotDto.AbandonedView> abandoned = abandonedRepository
                .findByAbandonedAtGreaterThanEqualAndDismissedFalseOrderByAbandonedAtDesc(startOfToday())
                .stream()
                .map(a -> toAbandonedView(a, HHMM))
                .toList();

        List<LiveSnapshotDto.ActivityView> activity = new ArrayList<>(recentActivity);

        return new LiveSnapshotDto(visitors, todayVisitors.size(), todayOrders.get(), carts, checkouts, abandoned, activity);
    }

    /** Нулира дневните агрегати при смяна на календарния ден (часова зона Europe/Sofia). */
    private void rollStatsDayIfNeeded() {
        LocalDate today = LocalDate.now(ZONE);
        if (!today.equals(statsDay)) {
            synchronized (this) {
                if (!today.equals(statsDay)) {
                    todayVisitors.clear();
                    todayOrders.set(0);
                    statsDay = today;
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Помощни
    // ---------------------------------------------------------------------
    private SiteEntity resolveActiveSite(String domain) {
        if (domain == null) return null;
        String d = domain.replace("https://", "").replace("http://", "").replaceAll("/+$", "").trim();
        SiteEntity site = siteRepository.findSiteEntityByUrl(d);
        if (site == null || !site.isActive()) return null;
        return site;
    }

    private void incrementStat(Long siteId, LiveEventDto e, String field) {
        if (e.getProductId() == null && e.getSku() == null) return;
        Long pid = e.getProductId() != null ? e.getProductId() : -1L;
        try {
            LiveProductStatEntity row = statRepository
                    .findBySiteIdAndProductWpIdAndStatDate(siteId, pid, LocalDate.now(ZONE))
                    .orElseGet(() -> {
                        LiveProductStatEntity r = new LiveProductStatEntity();
                        r.setSiteId(siteId);
                        r.setProductWpId(pid);
                        r.setStatDate(LocalDate.now(ZONE));
                        return r;
                    });
            if (e.getSku() != null) row.setSku(e.getSku());
            if (e.getProductName() != null) row.setName(e.getProductName());
            if (e.getProductImage() != null) row.setImageUrl(e.getProductImage());
            switch (field) {
                case "views" -> row.setViews(row.getViews() + 1);
                case "add_to_cart" -> row.setAddToCart(row.getAddToCart() + 1);
                case "checkout_starts" -> row.setCheckoutStarts(row.getCheckoutStarts() + 1);
                case "orders" -> row.setOrders(row.getOrders() + 1);
            }
            statRepository.save(row);
        } catch (Exception ex) {
            log.warn("Live: грешка при статистика за продукт {}: {}", pid, ex.getMessage());
        }
    }

    /**
     * Увеличава дневната статистика „добавяне в количка" за всеки НОВ продукт в количката —
     * веднъж на продукт на сесия (за да не се трупа при всяко cart_update при навигация).
     */
    private void incrementAddToCartForNewItems(Long siteId, LiveSession s, List<LiveEventDto.Item> items) {
        if (items == null) return;
        for (LiveEventDto.Item it : items) {
            Long pid = it.getProductId();
            if (pid == null) continue;
            if (s.addedProductIds.add(pid)) { // add() връща true само първия път за продукта
                LiveEventDto e = new LiveEventDto();
                e.setProductId(pid);
                e.setSku(it.getSku());
                e.setProductName(it.getName());
                e.setProductImage(it.getImage());
                incrementStat(siteId, e, "add_to_cart");
            }
        }
    }

    private void incrementCheckoutStartsForItems(Long siteId, LiveSession s) {
        if (s.items == null) return;
        for (LiveEventDto.Item it : s.items) {
            LiveEventDto e = new LiveEventDto();
            e.setProductId(it.getProductId());
            e.setSku(it.getSku());
            e.setProductName(it.getName());
            e.setProductImage(it.getImage());
            incrementStat(siteId, e, "checkout_starts");
        }
    }

    private void incrementOrdersForItems(Long siteId, LiveSession s, LiveEventDto ev) {
        List<LiveEventDto.Item> list = (ev.getItems() != null && !ev.getItems().isEmpty()) ? ev.getItems() : s.items;
        if (list == null) return;
        for (LiveEventDto.Item it : list) {
            LiveEventDto e = new LiveEventDto();
            e.setProductId(it.getProductId());
            e.setSku(it.getSku());
            e.setProductName(it.getName());
            e.setProductImage(it.getImage());
            incrementStat(siteId, e, "orders");
        }
    }

    /**
     * Записва „напусната каса" най-много ВЕДНЪЖ на сесия — пази от дублиране
     * (напр. две „leave" събития подред или leave + изтичане на сесията).
     */
    private void persistAbandonedOnce(LiveSession s) {
        synchronized (s) {
            if (s.stage != Stage.CHECKOUT || !s.hasData || s.abandonedPersisted) return;
            s.abandonedPersisted = true;
            persistAbandoned(s);
            addActivity("abandon", "Напусната каса",
                    (s.name != null ? s.name + " – " : "") + money(s.cartValue, s.currency));
        }
    }

    private void persistAbandoned(LiveSession s) {
        try {
            // Дедупликация по уникален клиент (сайт + сесиен токен): ако вече имаме
            // lead за този клиент — обновяваме го; иначе създаваме нов. Така 5 връщания
            // от касата дават 1 запис, който само се актуализира/натрупва.
            LiveAbandonedCheckoutEntity a = (s.sessionToken != null)
                    ? abandonedRepository
                        .findFirstBySiteIdAndSessionTokenOrderByAbandonedAtDesc(s.siteId, s.sessionToken)
                        .orElse(null)
                    : null;
            if (a == null) {
                a = new LiveAbandonedCheckoutEntity();
                a.setSiteId(s.siteId);
                a.setSessionToken(s.sessionToken);
                a.setStatus("НАПУСНАТА");
            }
            // Натрупване на контактите: нова непразна стойност печели, иначе пазим старата.
            a.setName(mergeField(a.getName(), s.name));
            a.setPhone(mergeField(a.getPhone(), s.phone));
            a.setEmail(mergeField(a.getEmail(), s.email));
            a.setCartValue(s.cartValue);
            a.setCurrency(s.currency);
            a.setAbandonedAt(Instant.now());
            try {
                a.setProductsJson(objectMapper.writeValueAsString(s.items));
            } catch (Exception ignore) { /* без продукти, ако сериализацията се провали */ }
            abandonedRepository.save(a);
        } catch (Exception ex) {
            log.warn("Live: грешка при запис на напусната каса: {}", ex.getMessage());
        }
    }

    /** За натрупване на контактни данни: нова непразна стойност печели, иначе пазим старата. */
    private String mergeField(String existing, String incoming) {
        return (incoming != null && !incoming.isBlank()) ? incoming : existing;
    }

    /**
     * Клиентът е напуснал касата (разглежда продукти/друга страница), но още има количка
     * → връща се в „Активни колички" (стадий CART), без статус на касата.
     * Нулира abandonedPersisted, за да може нов епизод на касата пак да се запише.
     */
    private void leaveCheckoutToCart(LiveSession s) {
        if (s.stage == Stage.CHECKOUT) {
            s.stage = Stage.CART;
            s.checkoutStatus = null;
            s.abandonedPersisted = false;
        }
    }

    private void addActivity(String type, String title, String sub) {
        recentActivity.addFirst(new LiveSnapshotDto.ActivityView(type, title, sub, HMS.format(Instant.now())));
        while (recentActivity.size() > 12) recentActivity.pollLast();
    }

    private int itemCount(LiveSession s) {
        if (s.items == null) return 0;
        return s.items.stream().mapToInt(i -> i.getQty() != null ? i.getQty() : 1).sum();
    }

    /** URL-и на снимките на продуктите в сесията (за таблото), без празни, до 8 бр. */
    private List<String> imagesOf(LiveSession s) {
        if (s.items == null) return List.of();
        return s.items.stream()
                .map(LiveEventDto.Item::getImage)
                .filter(img -> img != null && !img.isBlank())
                .limit(8)
                .toList();
    }

    private Instant startOfToday() {
        return LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
    }

    /**
     * Разчита JSON снимка на продукти (products_json) в списък от продукти за таблото.
     * Публичен — преизползва се и от LiveController за списъците „количка без каса" / „каса без данни".
     */
    public List<LiveSnapshotDto.AbandonedView.AbandonedItem> parseItems(String json) {
        return parseAbandonedItems(json);
    }

    /**
     * Преобразува запис „напусната каса" в готов вид за таблото (с продукти и снимки).
     * Публичен — общ за живия снапшот и за историята по дата (GET /live/abandoned).
     * @param timeFmt форматът за часа (HH:mm за днес, dd.MM HH:mm за историята).
     */
    public LiveSnapshotDto.AbandonedView toAbandonedView(LiveAbandonedCheckoutEntity a, DateTimeFormatter timeFmt) {
        return new LiveSnapshotDto.AbandonedView(
                a.getId(), a.getSiteId(),
                a.getName(), a.getEmail(), a.getPhone(), a.getCartValue(), a.getCurrency(),
                a.getAbandonedAt() != null ? timeFmt.format(a.getAbandonedAt()) : "",
                parseAbandonedItems(a.getProductsJson()));
    }

    /** Форматът за час в историята (с дата), достъпен за контролера. */
    public static DateTimeFormatter historyTimeFormat() {
        return DATE_HHMM;
    }

    private List<LiveSnapshotDto.AbandonedView.AbandonedItem> parseAbandonedItems(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<LiveEventDto.Item> raw = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, LiveEventDto.Item.class));
            return raw.stream()
                    .filter(it -> it.getProductId() != null)
                    .map(it -> new LiveSnapshotDto.AbandonedView.AbandonedItem(
                            it.getProductId(),
                            it.getSku(),
                            it.getName(),
                            it.getImage(),
                            it.getQty(),
                            it.getPrice() != null ? it.getPrice().doubleValue() : null))
                    .toList();
        } catch (Exception ex) {
            log.warn("Live: грешка при парсиране на productsJson: {}", ex.getMessage());
            return List.of();
        }
    }

    private String money(BigDecimal v, String currency) {
        if (v == null) return "";
        return v + " " + (currency != null ? currency : "");
    }
}
