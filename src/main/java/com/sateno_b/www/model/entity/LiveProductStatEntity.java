package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Дневна агрегирана статистика за продукт от Live проследяването.
 * Един ред = (сайт + продукт + ден). Броячите се увеличават при събития от сайта.
 * Така филтрите по период (днес/вчера/7 дни/месец) са бързи — само сумиране по дни.
 *
 * Добавяща таблица (нова) — старият код не я ползва, rollback е безопасен.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "live_product_stat",
        uniqueConstraints = @UniqueConstraint(columnNames = {"site_id", "product_wp_id", "stat_date"}),
        indexes = {
                @Index(name = "idx_live_stat_site_date", columnList = "site_id, stat_date")
        }
)
public class LiveProductStatEntity extends BaseEntity {

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "product_wp_id")
    private Long productWpId;

    @Column
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String name;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "stat_date")
    private LocalDate statDate;

    @Column(columnDefinition = "integer default 0")
    private int views = 0;

    @Column(name = "add_to_cart", columnDefinition = "integer default 0")
    private int addToCart = 0;

    @Column(name = "checkout_starts", columnDefinition = "integer default 0")
    private int checkoutStarts = 0;

    @Column(columnDefinition = "integer default 0")
    private int orders = 0;
}
