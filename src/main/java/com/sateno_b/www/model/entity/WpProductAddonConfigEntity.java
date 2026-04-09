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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private WpProductEntity product; // тука трябва да се сложи таблицата.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "value_id")
    private WpAddonValueEntity addonValue;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "site_id")
//    private SiteEntity site; адоните стават еднакви за всички сайтове!

    @Column
    private BigDecimal priceModifier; //Допълнителна цена

    private boolean isActive = true;
}
