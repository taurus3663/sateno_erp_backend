package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Напусната каса (lead). Записва се, когато клиент е стигнал до касата и е въвел
 * (поне частично) данни, но не е завършил поръчката и сесията му е изтекла.
 * Целта: да се обадим/пишем на клиента, за да завърши поръчката.
 *
 * GDPR: пазим данните (по решение „запазваме, после филтрираме"); полето consent
 * и contacted позволяват по-късно филтриране/анонимизиране.
 *
 * Добавяща таблица (нова) — старият код не я ползва, rollback е безопасен.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "live_abandoned_checkout",
        indexes = {
                @Index(name = "idx_live_aband_site", columnList = "site_id"),
                @Index(name = "idx_live_aband_session", columnList = "session_token")
        }
)
public class LiveAbandonedCheckoutEntity extends BaseEntity {

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "session_token")
    private String sessionToken;

    @Column
    private String name;

    @Column
    private String phone;

    @Column
    private String email;

    @Column(name = "cart_value")
    private BigDecimal cartValue;

    @Column
    private String currency;

    /** Продукти в количката към момента на напускане — JSON масив (текст). */
    @Column(name = "products_json", columnDefinition = "TEXT")
    private String productsJson;

    @Column(name = "abandoned_at")
    private Instant abandonedAt;

    /** Статус на лийда: НАПУСНАТА (по подразбиране), и др. при последваща обработка. */
    @Column
    private String status = "НАПУСНАТА";

    /** Дал ли е клиентът съгласие за съхранение/контакт (GDPR). */
    @Column(columnDefinition = "boolean default false")
    private boolean consent = false;

    /** Свързали ли сме се вече с клиента. */
    @Column(columnDefinition = "boolean default false")
    private boolean contacted = false;

    /**
     * Ръчно скрит от таблото „Напуснати каси" (бутон „Отказ").
     * Данните се ПАЗЯТ — записът само не се показва повече и не се връща след опресняване.
     * Добавяща колона — старият код не я ползва, rollback е безопасен.
     */
    @Column(columnDefinition = "boolean default false")
    private boolean dismissed = false;

    /** Кога е бил скрит (за история/справка). */
    @Column(name = "dismissed_at")
    private Instant dismissedAt;
}
