package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Обобщен „intelligence" профил на клиент (Фаза 2 на AI Sales Assistant).
 * Един ред = един клиент. Пази изчисления Lead Score + факторите зад него,
 * за да захранва таблото и AI препоръките.
 *
 * Изчислява се по график (нощно ~03:00) от {@code LeadScoreService}.
 *
 * Добавяща таблица (нова) — старият код не я ползва, rollback е безопасен.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "customer_intelligence",
        uniqueConstraints = @UniqueConstraint(name = "uq_cust_intel_customer", columnNames = {"customer_id"}),
        indexes = {
                @Index(name = "idx_cust_intel_score", columnList = "lead_score")
        }
)
public class CustomerIntelligenceEntity extends BaseEntity {

    @Column(name = "customer_id")
    private Long customerId;

    /** 0–100. */
    @Column(name = "lead_score", columnDefinition = "integer default 0")
    private int leadScore = 0;

    /** „горещ" | „топъл" | „студен" (по прага на score). */
    @Column(name = "lead_tier")
    private String leadTier;

    // --- фактори зад скора (за прозрачност/дебъг и показване) ---
    @Column(name = "orders_count", columnDefinition = "integer default 0")
    private int ordersCount = 0;

    @Column(name = "orders_value")
    private BigDecimal ordersValue = BigDecimal.ZERO;

    @Column(name = "last_order_at")
    private Instant lastOrderAt;

    @Column(name = "recency_days")
    private Integer recencyDays;

    /** Изоставени каси — попълва се след слоя за идентичност (сесия→клиент). Засега 0. */
    @Column(name = "abandoned_carts", columnDefinition = "integer default 0")
    private int abandonedCarts = 0;

    /** Рекламен източник — попълва се след идентичност/атрибуция. Засега null. */
    @Column(name = "ad_source")
    private String adSource;

    @Column(name = "computed_at")
    private Instant computedAt;
}
