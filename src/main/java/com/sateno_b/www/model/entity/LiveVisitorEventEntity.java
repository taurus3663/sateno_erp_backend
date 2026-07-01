package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Трайна събитийна история от Live проследяването — по сесия (и по-късно по клиент).
 * Един ред = едно значимо събитие (посещение на страница, разглеждане на продукт/категория,
 * количка, стъпка на касата, поръчка). Heartbeat събитията НЕ се записват тук — те само
 * поддържат „живото" състояние в паметта и обновяват lastSeen в {@link LiveSessionEntity}.
 *
 * Цел: захранва „Хронология на активността", фунията и топ продукти/категории ПО КЛИЕНТ
 * от дизайна „Поведение на клиента".
 *
 * Добавяща таблица (нова) — старият код не я ползва, rollback е безопасен.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "live_visitor_event",
        indexes = {
                @Index(name = "idx_live_evt_site_session", columnList = "site_id, session_token"),
                @Index(name = "idx_live_evt_site_occurred", columnList = "site_id, occurred_at"),
                @Index(name = "idx_live_evt_customer", columnList = "customer_id")
        }
)
public class LiveVisitorEventEntity extends BaseEntity {

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "session_token")
    private String sessionToken;

    /** Свързва се с клиент по-късно (слой за идентичност). Може да е null. */
    @Column(name = "customer_id")
    private Long customerId;

    /** Тип: visitor | product_view | category_view | cart_update | checkout_start | checkout_data | checkout_step | order_complete | leave */
    @Column(name = "event_type")
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String page;

    @Column(name = "product_wp_id")
    private Long productWpId;

    @Column(name = "product_name", columnDefinition = "TEXT")
    private String productName;

    @Column(name = "category_wp_id")
    private Long categoryWpId;

    @Column(name = "category_name", columnDefinition = "TEXT")
    private String categoryName;

    @Column(name = "cart_value")
    private BigDecimal cartValue;

    @Column
    private String currency;

    /** Стъпка във фунията на касата: data | shipping | payment (по избор). */
    @Column(name = "checkout_step")
    private String checkoutStep;

    @Column(name = "occurred_at")
    private Instant occurredAt;
}
