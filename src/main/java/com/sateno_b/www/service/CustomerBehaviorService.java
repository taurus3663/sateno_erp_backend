package com.sateno_b.www.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.CustomerBehaviorDto;
import com.sateno_b.www.model.entity.CustomerEntity;
import com.sateno_b.www.model.entity.CustomerIntelligenceEntity;
import com.sateno_b.www.model.entity.LiveAbandonedCheckoutEntity;
import com.sateno_b.www.model.entity.LiveSessionEntity;
import com.sateno_b.www.model.entity.LiveVisitorEventEntity;
import com.sateno_b.www.model.repository.CustomerIntelligenceRepository;
import com.sateno_b.www.model.repository.CustomerRepository;
import com.sateno_b.www.model.repository.LiveAbandonedCheckoutRepository;
import com.sateno_b.www.model.repository.LiveSessionRepository;
import com.sateno_b.www.model.repository.LiveVisitorEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Агрегира „Поведение на клиента" за дашборда (по дизайна).
 * Всичко се смята от вече събраните Live данни за конкретен клиент (след слоя за идентичност).
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class CustomerBehaviorService {

    private final LiveSessionRepository sessionRepository;
    private final LiveVisitorEventRepository eventRepository;
    private final LiveAbandonedCheckoutRepository abandonedRepository;
    private final CustomerIntelligenceRepository intelligenceRepository;
    private final CustomerRepository customerRepository;
    private final ObjectMapper objectMapper;

    public CustomerBehaviorDto build(Long customerId) {
        CustomerBehaviorDto dto = new CustomerBehaviorDto();
        dto.setCustomerId(customerId);

        List<LiveSessionEntity> sessions = sessionRepository.findByCustomerIdOrderByLastSeenDesc(customerId);
        List<LiveVisitorEventEntity> events = eventRepository.findByCustomerIdOrderByOccurredAtDesc(customerId);
        CustomerIntelligenceEntity ci = intelligenceRepository.findByCustomerId(customerId).orElse(null);
        CustomerEntity cust = customerRepository.findById(customerId).orElse(null);

        // --- профил / контакти ---
        dto.setName(firstNonBlank(
                cust != null ? joinName(cust.getFirstName(), cust.getLastName()) : null,
                sessions.stream().map(LiveSessionEntity::getName).filter(this::nb).findFirst().orElse(null)));
        dto.setPhone(firstNonBlank(cust != null ? cust.getPhone() : null,
                sessions.stream().map(LiveSessionEntity::getPhone).filter(this::nb).findFirst().orElse(null)));
        dto.setEmail(firstNonBlank(cust != null ? cust.getEmail() : null,
                sessions.stream().map(LiveSessionEntity::getEmail).filter(this::nb).findFirst().orElse(null)));

        dto.setFirstSeen(sessions.stream().map(LiveSessionEntity::getFirstSeen).filter(Objects::nonNull).min(Instant::compareTo).orElse(null));
        dto.setLastSeen(sessions.stream().map(LiveSessionEntity::getLastSeen).filter(Objects::nonNull).max(Instant::compareTo).orElse(null));
        dto.setTotalVisits(sessions.size());
        dto.setTotalTimeText(null); // не се събира още

        if (ci != null) {
            dto.setLeadScore(ci.getLeadScore());
            dto.setLeadTier(ci.getLeadTier());
        }

        // източник (първо докосване) — най-старата сесия с UTM/referrer
        LiveSessionEntity oldest = sessions.stream()
                .filter(s -> s.getFirstSeen() != null)
                .min(Comparator.comparing(LiveSessionEntity::getFirstSeen)).orElse(null);
        if (oldest != null) {
            dto.setSource(mapSource(oldest.getUtmSource(), oldest.getReferrer()));
            dto.setSourceCampaign(oldest.getUtmCampaign());
        }

        // --- KPI ---
        dto.setPageviews(sessions.stream().mapToInt(LiveSessionEntity::getPageviews).sum());
        dto.setAddToCarts(sessions.stream().mapToInt(LiveSessionEntity::getAddToCarts).sum());
        dto.setCheckoutStarts(sessions.stream().mapToInt(LiveSessionEntity::getCheckoutStarts).sum());
        if (ci != null) {
            dto.setCompletedOrders(ci.getOrdersCount());
            dto.setCompletedValue(ci.getOrdersValue() != null ? ci.getOrdersValue() : BigDecimal.ZERO);
        } else {
            dto.setCompletedOrders(sessions.stream().mapToInt(LiveSessionEntity::getOrders).sum());
        }
        dto.setCurrency(events.stream().map(LiveVisitorEventEntity::getCurrency).filter(this::nb).findFirst().orElse("лв."));

        // --- изоставени каси (по сесийните токени) ---
        List<String> tokens = sessions.stream().map(LiveSessionEntity::getSessionToken).filter(this::nb).distinct().collect(Collectors.toList());
        if (!tokens.isEmpty()) {
            List<LiveAbandonedCheckoutEntity> ab = abandonedRepository.findBySessionTokenInOrderByAbandonedAtDesc(tokens);
            dto.setAbandonedCount(ab.size());
            BigDecimal sum = BigDecimal.ZERO;
            for (LiveAbandonedCheckoutEntity a : ab) {
                if (a.getCartValue() != null) sum = sum.add(a.getCartValue());
                CustomerBehaviorDto.Abandoned row = new CustomerBehaviorDto.Abandoned();
                row.setDate(a.getAbandonedAt());
                row.setProducts(countProducts(a.getProductsJson()));
                row.setValue(a.getCartValue());
                row.setCurrency(a.getCurrency());
                dto.getAbandoned().add(row);
            }
            dto.setAbandonedValue(sum);
        }

        // --- топ категории / продукти ---
        dto.setTopCategories(topSlices(events, "category_view", LiveVisitorEventEntity::getCategoryName, 5));
        dto.setTopProducts(topSlices(events, "product_view", LiveVisitorEventEntity::getProductName, 5));

        // --- фуния ---
        int addToCart = dto.getAddToCarts();
        int checkoutStart = dto.getCheckoutStarts();
        int dataFilled = (int) events.stream().filter(e -> "checkout_data".equals(e.getEventType())).count();
        int shipping = (int) events.stream().filter(e -> "shipping".equalsIgnoreCase(e.getCheckoutStep())).count();
        int payment = (int) events.stream().filter(e -> "payment".equalsIgnoreCase(e.getCheckoutStep())).count();
        int completed = dto.getCompletedOrders();
        int base = Math.max(addToCart, 1);
        dto.getFunnel().add(new CustomerBehaviorDto.FunnelStep("Добавяне в количка", addToCart, pct(addToCart, base)));
        dto.getFunnel().add(new CustomerBehaviorDto.FunnelStep("Започване на Checkout", checkoutStart, pct(checkoutStart, base)));
        dto.getFunnel().add(new CustomerBehaviorDto.FunnelStep("Попълнени данни", dataFilled, pct(dataFilled, base)));
        if (shipping > 0) dto.getFunnel().add(new CustomerBehaviorDto.FunnelStep("Избор на доставка", shipping, pct(shipping, base)));
        if (payment > 0) dto.getFunnel().add(new CustomerBehaviorDto.FunnelStep("Избор на плащане", payment, pct(payment, base)));
        dto.getFunnel().add(new CustomerBehaviorDto.FunnelStep("Завършена поръчка", completed, pct(completed, base)));

        // --- устройства ---
        dto.setDevices(distribution(sessions.stream()
                .map(s -> nb(s.getDevice()) ? deviceLabel(s.getDevice()) : "Друго")
                .collect(Collectors.toList())));

        // --- локация (geo още не се събира) ---
        dto.setLocations(new ArrayList<>());

        // --- източници на трафик ---
        dto.setTrafficSources(distribution(sessions.stream()
                .map(s -> mapSource(s.getUtmSource(), s.getReferrer()))
                .collect(Collectors.toList())));

        // --- хронология ---
        Map<String, String> tokenDevice = new HashMap<>();
        for (LiveSessionEntity s : sessions) {
            if (nb(s.getSessionToken())) tokenDevice.put(s.getSessionToken(), deviceLabel(s.getDevice()));
        }
        dto.setTimeline(events.stream().limit(15).map(e -> toActivity(e, tokenDevice)).collect(Collectors.toList()));

        return dto;
    }

    // ---------- helpers ----------

    private List<CustomerBehaviorDto.Slice> topSlices(List<LiveVisitorEventEntity> events, String type,
                                                      java.util.function.Function<LiveVisitorEventEntity, String> field, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LiveVisitorEventEntity e : events) {
            if (!type.equals(e.getEventType())) continue;
            String key = field.apply(e);
            if (!nb(key)) continue;
            counts.merge(key, 1, Integer::sum);
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(limit)
                .map(en -> new CustomerBehaviorDto.Slice(en.getKey(), en.getValue(), pct(en.getValue(), Math.max(total, 1))))
                .collect(Collectors.toList());
    }

    private List<CustomerBehaviorDto.Slice> distribution(List<String> labels) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String l : labels) counts.merge(l, 1, Integer::sum);
        int total = labels.size();
        return counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(en -> new CustomerBehaviorDto.Slice(en.getKey(), en.getValue(), pct(en.getValue(), Math.max(total, 1))))
                .collect(Collectors.toList());
    }

    private CustomerBehaviorDto.Activity toActivity(LiveVisitorEventEntity e, Map<String, String> tokenDevice) {
        CustomerBehaviorDto.Activity a = new CustomerBehaviorDto.Activity();
        a.setTime(e.getOccurredAt());
        a.setDevice(tokenDevice.getOrDefault(e.getSessionToken(), null));
        switch (e.getEventType() == null ? "" : e.getEventType()) {
            case "visitor" -> { a.setType("visitor"); a.setTitle("Посети сайта"); a.setSub(e.getPage()); }
            case "product_view" -> { a.setType("product"); a.setTitle("Разгледа продукт"); a.setSub(e.getProductName()); }
            case "category_view" -> { a.setType("category"); a.setTitle("Разгледа категория"); a.setSub(e.getCategoryName()); }
            case "cart_update" -> { a.setType("cart"); a.setTitle("Добави в количка"); a.setSub(e.getProductName()); }
            case "checkout_start" -> { a.setType("checkout"); a.setTitle("Започна Checkout"); }
            case "checkout_data" -> { a.setType("checkout"); a.setTitle("Попълни лични данни"); }
            case "order_complete" -> { a.setType("order"); a.setTitle("Завърши поръчка"); }
            case "leave" -> { a.setType("leave"); a.setTitle("Напусна сайта"); }
            default -> { a.setType("visitor"); a.setTitle(e.getEventType()); }
        }
        return a;
    }

    private String mapSource(String utm, String referrer) {
        if (nb(utm)) {
            String u = utm.toLowerCase();
            if (u.contains("facebook") || u.equals("fb")) return "Facebook Ads";
            if (u.contains("instagram") || u.equals("ig")) return "Instagram Ads";
            if (u.contains("google")) return "Google Ads";
            return capitalize(utm);
        }
        if (nb(referrer)) {
            String r = referrer.toLowerCase();
            if (r.contains("google")) return "Google Organic";
            if (r.contains("facebook")) return "Facebook";
            if (r.contains("instagram")) return "Instagram";
            return "Реферал";
        }
        return "Директно";
    }

    private String deviceLabel(String device) {
        if (device == null) return "Друго";
        return switch (device) {
            case "mobile" -> "Мобилни";
            case "desktop" -> "Десктоп";
            case "tablet" -> "Таблети";
            default -> capitalize(device);
        };
    }

    private int countProducts(String productsJson) {
        if (!nb(productsJson)) return 0;
        try {
            var node = objectMapper.readTree(productsJson);
            if (node.isArray()) return node.size();
        } catch (Exception ignore) { }
        return 1;
    }

    private int pct(int count, int total) {
        if (total <= 0) return 0;
        return (int) Math.round(100.0 * count / total);
    }

    private boolean nb(String s) { return s != null && !s.isBlank(); }

    private String firstNonBlank(String a, String b) { return nb(a) ? a : (nb(b) ? b : null); }

    private String joinName(String first, String last) {
        String n = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return n.isEmpty() ? null : n;
    }

    private String capitalize(String s) {
        if (!nb(s)) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
