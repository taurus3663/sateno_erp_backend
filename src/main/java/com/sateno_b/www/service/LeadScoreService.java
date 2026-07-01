package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.CustomerIntelligenceEntity;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.repository.CustomerIntelligenceRepository;
import com.sateno_b.www.model.repository.WpOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Изчислява Lead Score за клиентите (Фаза 2 на AI Sales Assistant).
 *
 * Тежести (потвърдени от Асан):
 *   скорошност 30% + честота 20% + стойност поръчки 25% + изоставена каса 15% + рекламен източник 10%.
 *
 * Забележка (v1): компонентите „изоставена каса" и „рекламен източник" изискват слоя за
 * ИДЕНТИЧНОСТ (сесия→клиент), който е отложен. Докато го няма, те са 0 — резултатът се
 * формира от поръчковите фактори (скорошност+честота+стойност = до 75 точки). Когато
 * идентичността се добави, тук само се включват липсващите 25 точки, без друга промяна.
 *
 * Работи по нощен график (~03:00 Europe/Sofia). Може да се пусне и ръчно чрез {@link #recomputeAll()}.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class LeadScoreService {

    private final WpOrderRepository orderRepository;
    private final CustomerIntelligenceRepository intelligenceRepository;

    // --- тежести (сума = 100) ---
    private static final int W_RECENCY = 30;
    private static final int W_FREQUENCY = 20;
    private static final int W_ORDER_VALUE = 25;
    private static final int W_ABANDONED = 15;   // изисква идентичност — засега 0
    private static final int W_AD_SOURCE = 10;   // изисква идентичност/атрибуция — засега 0

    // --- прагове за нормализация (нагласими) ---
    private static final double RECENCY_WINDOW_DAYS = 180.0; // по-нова от 6 месеца носи точки
    private static final double FREQUENCY_MAX = 5.0;         // 5+ поръчки = макс
    private static final double ORDER_VALUE_MAX = 500.0;     // 500+ (валута на поръчката) = макс

    // Статуси, които броим за „истинска" поръчка (изключваме отказани/провалени/изоставени).
    private static final List<OrderStatus> COUNTED_STATUSES = List.of(
            OrderStatus.PROCESSING, OrderStatus.SENT, OrderStatus.APPROVED,
            OrderStatus.COMPLETED, OrderStatus.JOINT
    );

    /** Нощно преизчисляване на всички лийдове. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/Sofia")
    public void scheduledRecompute() {
        log.info("LeadScore: нощно преизчисляване стартира");
        int n = recomputeAll();
        log.info("LeadScore: преизчислени {} клиентски профила", n);
    }

    /** Преизчислява Lead Score за всички клиенти с поръчки. Връща броя обработени. */
    @Transactional
    public int recomputeAll() {
        Instant now = Instant.now();
        List<WpOrderRepository.CustomerOrderAggregate> aggregates =
                orderRepository.aggregateByCustomer(COUNTED_STATUSES);

        int processed = 0;
        for (WpOrderRepository.CustomerOrderAggregate a : aggregates) {
            if (a.getCustomerId() == null) continue;
            upsertForCustomer(a, now);
            processed++;
        }
        return processed;
    }

    private void upsertForCustomer(WpOrderRepository.CustomerOrderAggregate a, Instant now) {
        long ordersCount = a.getOrdersCount() != null ? a.getOrdersCount() : 0L;
        BigDecimal ordersValue = a.getOrdersValue() != null ? a.getOrdersValue() : BigDecimal.ZERO;
        Instant lastOrderAt = a.getLastOrderAt();

        Integer recencyDays = (lastOrderAt != null)
                ? (int) Duration.between(lastOrderAt, now).toDays()
                : null;

        double recencyF = (recencyDays != null)
                ? clamp01(1.0 - (recencyDays / RECENCY_WINDOW_DAYS))
                : 0.0;
        double frequencyF = clamp01(ordersCount / FREQUENCY_MAX);
        double valueF = clamp01(ordersValue.doubleValue() / ORDER_VALUE_MAX);
        double abandonedF = 0.0; // TODO: след слой за идентичност
        double adSourceF = 0.0;  // TODO: след атрибуция по клиент

        int score = (int) Math.round(
                W_RECENCY * recencyF
                        + W_FREQUENCY * frequencyF
                        + W_ORDER_VALUE * valueF
                        + W_ABANDONED * abandonedF
                        + W_AD_SOURCE * adSourceF
        );
        score = Math.max(0, Math.min(100, score));

        CustomerIntelligenceEntity ci = intelligenceRepository
                .findByCustomerId(a.getCustomerId())
                .orElseGet(() -> {
                    CustomerIntelligenceEntity e = new CustomerIntelligenceEntity();
                    e.setCustomerId(a.getCustomerId());
                    return e;
                });
        ci.setOrdersCount((int) ordersCount);
        ci.setOrdersValue(ordersValue.setScale(2, RoundingMode.HALF_UP));
        ci.setLastOrderAt(lastOrderAt);
        ci.setRecencyDays(recencyDays);
        ci.setLeadScore(score);
        ci.setLeadTier(tierOf(score));
        ci.setComputedAt(now);
        intelligenceRepository.save(ci);
    }

    private String tierOf(int score) {
        if (score >= 70) return "горещ";
        if (score >= 40) return "топъл";
        return "студен";
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
