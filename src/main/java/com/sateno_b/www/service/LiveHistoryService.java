package com.sateno_b.www.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.LiveEventDto;
import com.sateno_b.www.model.entity.LiveSessionEntity;
import com.sateno_b.www.model.entity.LiveVisitorEventEntity;
import com.sateno_b.www.model.repository.LiveSessionRepository;
import com.sateno_b.www.model.repository.LiveVisitorEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Трайна история от Live проследяването (Фаза 1 на AI Sales Assistant).
 * Пази обобщение по сесия ({@link LiveSessionEntity}) + детайлна хронология
 * ({@link LiveVisitorEventEntity}) — за дизайна „Поведение на клиента".
 *
 * Работи в СОБСТВЕНА транзакция ({@link Propagation#REQUIRES_NEW}), за да не може
 * проблем при запис на историята да развали основния прием на събития/статистики
 * в {@link LiveTrackingService}. Историята е вторична — при грешка само се логва.
 *
 * heartbeat събития НЕ се пазят (за да не товарим базата) — те само поддържат
 * „живото" състояние в паметта на LiveTrackingService.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class LiveHistoryService {

    private final LiveSessionRepository sessionRepository;
    private final LiveVisitorEventRepository visitorEventRepository;
    private final IdentityResolutionService identityResolutionService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(Long siteId, LiveEventDto e) {
        String type = e.getType();
        if (type == null || "heartbeat".equals(type) || e.getSession() == null) return;
        try {
            upsertLiveSession(siteId, type, e);
            saveVisitorEvent(siteId, type, e);
            // Слой за идентичност: при контакт (checkout_data) или поръчка — опит за свързване с клиент.
            if ("checkout_data".equals(type) || "order_complete".equals(type)) {
                identityResolutionService.tryResolveFromEvent(siteId, e);
            }
        } catch (Exception ex) {
            log.warn("LiveHistory: грешка при запис на история ({}): {}", type, ex.getMessage());
        }
    }

    /** Обновява/създава обобщения ред за сесията (агрегати + устройство + източник + контакти). */
    private void upsertLiveSession(Long siteId, String type, LiveEventDto e) {
        Instant now = Instant.now();
        LiveSessionEntity ses = sessionRepository
                .findBySiteIdAndSessionToken(siteId, e.getSession())
                .orElseGet(() -> {
                    LiveSessionEntity s = new LiveSessionEntity();
                    s.setSiteId(siteId);
                    s.setSessionToken(e.getSession());
                    s.setFirstSeen(now);
                    return s;
                });
        ses.setLastSeen(now);

        // Устройство/браузър/ОС — парсва се веднъж от User-Agent.
        if (ses.getDevice() == null && e.getUserAgent() != null) {
            ses.setDevice(parseDevice(e.getUserAgent()));
            ses.setBrowser(parseBrowser(e.getUserAgent()));
            ses.setOs(parseOs(e.getUserAgent()));
        }
        // Източник — пазим само първото непразно (принцип „първо докосване").
        if (ses.getReferrer() == null && e.getReferrer() != null && !e.getReferrer().isBlank()) {
            ses.setReferrer(e.getReferrer());
        }
        if (ses.getUtmSource() == null && e.getUtmSource() != null) ses.setUtmSource(e.getUtmSource());
        if (ses.getUtmMedium() == null && e.getUtmMedium() != null) ses.setUtmMedium(e.getUtmMedium());
        if (ses.getUtmCampaign() == null && e.getUtmCampaign() != null) ses.setUtmCampaign(e.getUtmCampaign());

        // Контакти (ако клиентът въведе на касата) — нова непразна стойност печели.
        ses.setName(mergeField(ses.getName(), e.getName()));
        ses.setPhone(mergeField(ses.getPhone(), e.getPhone()));
        ses.setEmail(mergeField(ses.getEmail(), e.getEmail()));

        switch (type) {
            case "visitor" -> ses.setPageviews(ses.getPageviews() + 1);
            case "product_view" -> ses.setProductViews(ses.getProductViews() + 1);
            case "cart_update" -> {
                if (e.getProductId() != null || e.getSku() != null) ses.setAddToCarts(ses.getAddToCarts() + 1);
                updateCartSnapshot(ses, e);
            }
            case "checkout_start" -> {
                ses.setCheckoutStarts(ses.getCheckoutStarts() + 1);
                updateCartSnapshot(ses, e);
            }
            case "cart_clear" -> clearCartSnapshot(ses);
            case "order_complete" -> ses.setOrders(ses.getOrders() + 1);
            default -> { /* други типове — само обновяват lastSeen/контакти */ }
        }
        sessionRepository.save(ses);
    }

    /** Записва един ред в детайлната хронология (за timeline/фуния/топ по клиент). */
    private void saveVisitorEvent(Long siteId, String type, LiveEventDto e) {
        LiveVisitorEventEntity ev = new LiveVisitorEventEntity();
        ev.setSiteId(siteId);
        ev.setSessionToken(e.getSession());
        ev.setEventType(type);
        ev.setPage(e.getPage());
        ev.setProductWpId(e.getProductId());
        ev.setProductName(e.getProductName());
        ev.setCategoryWpId(e.getCategoryId());
        ev.setCategoryName(e.getCategoryName());
        ev.setCartValue(e.getCartValue());
        ev.setCurrency(e.getCurrency());
        ev.setCheckoutStep(e.getCheckoutStep());
        ev.setOccurredAt(Instant.now());
        visitorEventRepository.save(ev);
    }

    /**
     * Обновява снимката на количката за сесията (продукти + стойност + валута),
     * за да можем после да покажем „количка без каса" / „каса без данни" с продукти и снимки.
     * Пази последното известно съдържание — не се трие при преминаване към каса.
     */
    private void updateCartSnapshot(LiveSessionEntity ses, LiveEventDto e) {
        if (e.getItems() != null && !e.getItems().isEmpty()) {
            try {
                ses.setProductsJson(objectMapper.writeValueAsString(e.getItems()));
            } catch (Exception ignore) { /* без снимка, ако сериализацията се провали */ }
        }
        if (e.getCartValue() != null) ses.setCartValue(e.getCartValue());
        if (e.getCurrency() != null) ses.setCurrency(e.getCurrency());
    }

    /** Количката е изпразнена → махаме снимката, за да не се брои като „количка без каса". */
    private void clearCartSnapshot(LiveSessionEntity ses) {
        ses.setProductsJson(null);
        ses.setCartValue(null);
    }

    private String mergeField(String existing, String incoming) {
        return (incoming != null && !incoming.isBlank()) ? incoming : existing;
    }

    // --- прости евристики за устройство/браузър/ОС от User-Agent ---
    private String parseDevice(String ua) {
        if (ua == null) return null;
        String u = ua.toLowerCase();
        if (u.contains("ipad") || (u.contains("tablet") && !u.contains("mobile"))) return "tablet";
        if (u.contains("mobi") || u.contains("iphone") || u.contains("android")) return "mobile";
        return "desktop";
    }

    private String parseBrowser(String ua) {
        if (ua == null) return null;
        String u = ua.toLowerCase();
        if (u.contains("edg/") || u.contains("edge")) return "Edge";
        if (u.contains("opr/") || u.contains("opera")) return "Opera";
        if (u.contains("chrome") && !u.contains("chromium")) return "Chrome";
        if (u.contains("firefox")) return "Firefox";
        if (u.contains("safari")) return "Safari";
        return "Друг";
    }

    private String parseOs(String ua) {
        if (ua == null) return null;
        String u = ua.toLowerCase();
        if (u.contains("android")) return "Android";
        if (u.contains("iphone") || u.contains("ipad") || u.contains("ios")) return "iOS";
        if (u.contains("windows")) return "Windows";
        if (u.contains("mac os") || u.contains("macintosh")) return "macOS";
        if (u.contains("linux")) return "Linux";
        return "Друг";
    }
}
