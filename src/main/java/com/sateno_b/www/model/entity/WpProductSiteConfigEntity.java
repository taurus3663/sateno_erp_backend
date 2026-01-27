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
@Table(name = "wp_product_site_config")
public class WpProductSiteConfigEntity extends BaseEntity{

    @Column
    private Long wpProductId;
    @Column
    private String sku;

    @Column
    private BigDecimal price;
    @Column
    private BigDecimal regularPrice;
    @Column
    private String slug;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private WpProductEntity product;


    @ManyToOne
    @JoinColumn(name = "site_id")
    private SiteEntity site;
}
