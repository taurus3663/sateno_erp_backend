package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.LiveBasketView;
import com.sateno_b.www.model.dto.LiveEventDto;
import com.sateno_b.www.model.dto.LiveSnapshotDto;
import com.sateno_b.www.model.entity.LiveAbandonedCheckoutEntity;
import com.sateno_b.www.model.entity.LiveSessionEntity;
import com.sateno_b.www.model.repository.LiveAbandonedCheckoutRepository;
import com.sateno_b.www.model.repository.LiveProductStatRepository;
import com.sateno_b.www.model.repository.LiveSessionRepository;
import com.sateno_b.www.service.LiveTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Live проследяване — REST + прием на събития.
 * Реалният път е с префикс /erp (виж Webconfig): напр. /erp/live/event.
 */
@RestController
@RequiredArgsConstructor
@Log4j2
@RequestMapping("/live")
public class LiveController {

    private static final ZoneId ZONE = ZoneId.of("Europe/Sofia");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZONE);

    private final LiveTrackingService liveService;
    private final LiveProductStatRepository statRepository;
    private final LiveAbandonedCheckoutRepository abandonedRepository;
    private final LiveSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Публичен endpoint — приема събития от tracker-а на сайта.
     * Приема и application/json, и text/plain (navigator.sendBeacon праща text/plain),
     * затова четем суровото тяло и го парсваме ръчно.
     */
    @PostMapping(value = "/event", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> event(@RequestBody(required = false) String body,
                                      jakarta.servlet.http.HttpServletRequest request) {
        if (body != null && !body.isBlank()) {
            try {
                LiveEventDto dto = objectMapper.readValue(body, LiveEventDto.class);
                // Устройство/браузър и IP се вземат от HTTP хедърите на сървъра,
                // а не от тялото — по-надеждно и не изисква tracker промяна за UA.
                dto.setUserAgent(request.getHeader("User-Agent"));
                dto.setClientIp(clientIp(request));
                liveService.handleEvent(dto);
            } catch (Exception ex) {
                log.debug("Live: невалидно тяло на събитие: {}", ex.getMessage());
            }
        }
        return ResponseEntity.noContent().build();
    }

    /** Реалният IP на клиента зад reverse proxy (X-Forwarded-For), с fallback към remote addr. */
    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    /** Снапшот на живото състояние (за първоначално зареждане/polling fallback). */
    @GetMapping("/snapshot")
    public LiveSnapshotDto snapshot() {
        return liveService.buildSnapshot();
    }

    /** Най-разглеждани продукти за период. period = today | yesterday | 7d | month | custom */
    @GetMapping("/products/most-viewed")
    public List<MostViewedProduct> mostViewed(
            @RequestParam(defaultValue = "7d") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate today = LocalDate.now(ZONE);
        LocalDate f, t;
        switch (period) {
            case "today" -> { f = today; t = today; }
            case "yesterday" -> { f = today.minusDays(1); t = today.minusDays(1); }
            case "month" -> { f = today.withDayOfMonth(1); t = today; }
            case "custom" -> { f = from != null ? from : today; t = to != null ? to : today; }
            default -> { f = today.minusDays(6); t = today; } // 7d
        }

        List<MostViewedProduct> out = new ArrayList<>();
        int rank = 1;
        for (LiveProductStatRepository.ProductStatAggregate a : statRepository.aggregateForPeriod(f, t)) {
            MostViewedProduct p = new MostViewedProduct();
            p.setRank(rank++);
            p.setProductWpId(a.getProductWpId());
            p.setSku(a.getSku());
            p.setName(a.getName());
            p.setImageUrl(a.getImageUrl());
            p.setViews(nz(a.getViews()));
            p.setAddToCart(nz(a.getAddToCart()));
            p.setCheckoutStarts(nz(a.getCheckoutStarts()));
            p.setOrders(nz(a.getOrders()));
            out.add(p);
        }
        return out;
    }

    /**
     * Напуснати каси (lead-ове) за период — готов вид с продукти/снимки (както живия снапшот).
     * По подразбиране — днес.
     */
    @GetMapping("/abandoned")
    public List<LiveSnapshotDto.AbandonedView> abandoned(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<LiveAbandonedCheckoutEntity> rows;
        if (from == null && to == null) {
            Instant start = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
            rows = abandonedRepository.findByAbandonedAtGreaterThanEqualOrderByAbandonedAtDesc(start);
        } else {
            Instant[] range = dayRange(from, to);
            rows = abandonedRepository.findByAbandonedAtBetweenOrderByAbandonedAtDesc(range[0], range[1]);
        }
        return rows.stream()
                .map(a -> liveService.toAbandonedView(a, LiveTrackingService.historyTimeFormat()))
                .toList();
    }

    /**
     * „Продукти в количка (без каса)" за период — сесии, добавили в количка,
     * но нестигнали до каса. По подразбиране — днес. Групирано по кошница/сесия.
     */
    @GetMapping("/carts-no-checkout")
    public List<LiveBasketView> cartsNoCheckout(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant[] range = dayRange(from, to);
        return toBasketViews(sessionRepository.findCartsWithoutCheckout(range[0], range[1]));
    }

    /**
     * „Каси без въведени данни" за период — сесии, стигнали до каса, но без контакти
     * и без поръчка. По подразбиране — днес. Групирано по кошница/сесия.
     */
    @GetMapping("/checkouts-no-data")
    public List<LiveBasketView> checkoutsNoData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant[] range = dayRange(from, to);
        return toBasketViews(sessionRepository.findCheckoutsWithoutData(range[0], range[1]));
    }

    /** Диапазон [начало, край) по дни; по подразбиране — днес. */
    private Instant[] dayRange(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZONE);
        Instant f = (from != null ? from : today).atStartOfDay(ZONE).toInstant();
        Instant t = (to != null ? to : today).plusDays(1).atStartOfDay(ZONE).toInstant();
        return new Instant[]{f, t};
    }

    /** Преобразува сесии в кошници за таблото (с продукти/снимки от снимката на количката). */
    private List<LiveBasketView> toBasketViews(List<LiveSessionEntity> sessions) {
        List<LiveBasketView> out = new ArrayList<>();
        for (LiveSessionEntity s : sessions) {
            List<LiveSnapshotDto.AbandonedView.AbandonedItem> items = liveService.parseItems(s.getProductsJson());
            int count = items.stream().mapToInt(i -> i.getQty() != null ? i.getQty() : 1).sum();
            out.add(new LiveBasketView(
                    s.getId(), s.getSiteId(), s.getDevice(),
                    s.getCartValue(), s.getCurrency(), count,
                    s.getLastSeen() != null ? HM.format(s.getLastSeen()) : "",
                    items));
        }
        return out;
    }

    /**
     * „Отказ" от таблото — трайно скрива напусната каса (soft-dismiss).
     * ВАЖНО: НЕ трие данните — само вдига флага dismissed, така че записът
     * да не се връща след опресняване. Ако id-то не съществува, тихо 204.
     */
    @PostMapping("/abandoned/{id}/dismiss")
    public ResponseEntity<Void> dismissAbandoned(@PathVariable Long id) {
        abandonedRepository.findById(id).ifPresent(a -> {
            a.setDismissed(true);
            a.setDismissedAt(Instant.now());
            abandonedRepository.save(a);
        });
        return ResponseEntity.noContent().build();
    }

    private long nz(Long v) { return v != null ? v : 0L; }

    @Data
    public static class MostViewedProduct {
        private int rank;
        private Long productWpId;
        private String sku;
        private String name;
        private String imageUrl;
        private long views;
        private long addToCart;
        private long checkoutStarts;
        private long orders;
    }
}
