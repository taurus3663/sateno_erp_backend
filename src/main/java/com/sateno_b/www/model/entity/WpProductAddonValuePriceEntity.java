package com.sateno_b.www.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wp_product_addon_value_price")
public class WpProductAddonValuePriceEntity extends BaseEntity {

    @ManyToOne
    private WpProductEntity product;

    @ManyToOne
    private WpAddonValueEntity addonValue;

    @ManyToOne
    private SiteEntity site;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;
}
