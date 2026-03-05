package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.enums.ProductSaleType;
import com.sateno_b.www.model.enums.ProductStatus;
import com.sateno_b.www.model.enums.ProductUnit;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wp_product")
public class WpProductEntity extends BaseEntity {

    @Column
    private Integer stockQuantity;

    @Enumerated(EnumType.ORDINAL)
    @Column
    private ProductStatus status;

//    @Enumerated(EnumType.ORDINAL)
//    private ProductUnit unit;

    @Column
    private String weight;

    @Column
    private BigDecimal buyPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private WpBrandEntity brand;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    List<WpProductTranslationEntity> translations = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    List<WpProductImageEntity> images = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "wp_product_category_mapping",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<WpCategoryEntity> categories = new HashSet<>();

    // В WpProductEntity.java
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WpProductAddonConfigEntity> addonConfig = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WpProductSiteConfigEntity> siteConfigs = new ArrayList<>();

    @Enumerated(EnumType.ORDINAL)
    private ProductSaleType saleType;

}
