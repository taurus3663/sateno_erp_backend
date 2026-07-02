package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Обобщен профил на една сесия от Live проследяването (един ред = сайт + сесиен токен).
 * Натрупва фактите, нужни за дизайна „Поведение на клиента": първо/последно посещение,
 * брой посещения/разглеждания, устройство/браузър, източник (referrer/UTM), контакти,
 * и по-късно връзка към клиент (customerId) и геолокация.
 *
 * Обновява се при всяко значимо събитие (не при heartbeat). Детайлната хронология е в
 * {@link LiveVisitorEventEntity}; тук държим агрегатите за бързо показване.
 *
 * Добавяща таблица (нова) — старият код не я ползва, rollback е безопасен.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "live_session",
        uniqueConstraints = @UniqueConstraint(name = "uq_live_session_site_token", columnNames = {"site_id", "session_token"}),
        indexes = {
                @Index(name = "idx_live_session_customer", columnList = "customer_id"),
                @Index(name = "idx_live_session_last_seen", columnList = "site_id, last_seen")
        }
)
public class LiveSessionEntity extends BaseEntity {

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "session_token")
    private String sessionToken;

    /** Слой за идентичност (по-късно). Може да е null. */
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "first_seen")
    private Instant firstSeen;

    @Column(name = "last_seen")
    private Instant lastSeen;

    // --- устройство / браузър (парсва се от User-Agent на сървъра) ---
    @Column
    private String device;   // mobile | desktop | tablet
    @Column
    private String browser;
    @Column
    private String os;

    // --- източник на трафик ---
    @Column(columnDefinition = "TEXT")
    private String referrer;
    @Column(name = "utm_source")
    private String utmSource;
    @Column(name = "utm_medium")
    private String utmMedium;
    @Column(name = "utm_campaign")
    private String utmCampaign;

    // --- геолокация (попълва се по-късно от IP → geo) ---
    @Column(name = "geo_country")
    private String geoCountry;
    @Column(name = "geo_city")
    private String geoCity;

    // --- агрегатни броячи ---
    @Column(columnDefinition = "integer default 0")
    private int pageviews = 0;
    @Column(name = "product_views", columnDefinition = "integer default 0")
    private int productViews = 0;
    @Column(name = "add_to_carts", columnDefinition = "integer default 0")
    private int addToCarts = 0;
    @Column(name = "checkout_starts", columnDefinition = "integer default 0")
    private int checkoutStarts = 0;
    @Column(columnDefinition = "integer default 0")
    private int orders = 0;

    // --- контакти (ако клиентът е въвел на касата) ---
    @Column
    private String name;
    @Column
    private String phone;
    @Column
    private String email;

    // --- снимка на количката към последната активност (за списъците
    //     „количка без каса" и „каса без данни" + история по дата) ---
    /** Продукти в количката (JSON масив, същият формат като live_abandoned_checkout.products_json). */
    @Column(name = "products_json", columnDefinition = "TEXT")
    private String productsJson;

    /** Стойност на количката към последната активност. */
    @Column(name = "cart_value")
    private BigDecimal cartValue;

    /** Валута на количката (напр. EUR/BGN/RON). */
    @Column
    private String currency;

    /**
     * Кога клиентът е напуснал сайта (за списъците „количка без каса" / „каса без данни").
     * NULL = още е активен (пазарува) → показва се в „Активни колички/каси".
     * Попълва се: (1) веднага при явен `leave` сигнал от тракера, или
     * (2) като резерва — от cleanup() след ~60 сек без активност (изпуснат сигнал).
     * Всяко ново събитие го връща на NULL (клиентът се е върнал).
     */
    @Column(name = "left_at")
    private Instant leftAt;
}
