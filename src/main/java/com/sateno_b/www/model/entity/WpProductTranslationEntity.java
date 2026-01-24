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
@Table(name = "wp_product_translation")
public class WpProductTranslationEntity extends BaseEntity {

    @Column
    private Long wpProductId;

    @Column
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String shortDescription;

    @Column
    private String sku;

    @Column
    private BigDecimal price;
    @Column
    private BigDecimal regularPrice;
    @Column
    private String slug;


    @ManyToOne
    @JoinColumn(name = "site_id")
    private SiteEntity site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private WpProductEntity product;

    @ManyToOne
    @JoinColumn(name = "language_id")
    private LanguageEntity language;
}
