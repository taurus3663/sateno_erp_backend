package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wp_product_addon_config")
public class WpProductAddonConfigEntity extends BaseEntity {

    @Column
    private Long productId; // тука трябва да се сложи таблицата.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "value_id")
    private WpAddonValueEntity addonValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private SiteEntity site;

    @Column
    private BigDecimal priceModifier; //Допълнителна цена

    private boolean isActive = true;
}
