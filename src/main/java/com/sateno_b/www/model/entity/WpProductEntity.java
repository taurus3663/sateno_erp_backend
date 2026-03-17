package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.entity.data.Dimensions;
import com.sateno_b.www.model.enums.ProductSaleType;
import com.sateno_b.www.model.enums.ProductStatus;
import com.sateno_b.www.model.enums.ProductUnit;
import com.sateno_b.www.model.listeners.WpProductEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EntityListeners(WpProductEntityListener.class)
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

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    List<WpProductTranslationEntity> translations = new ArrayList<>();

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    List<WpProductImageEntity> images = new ArrayList<>();

    @BatchSize(size = 100)
    @ManyToMany
    @JoinTable(
            name = "wp_product_category_mapping",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<WpCategoryEntity> categories = new HashSet<>();

    // В WpProductEntity.java
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WpProductAddonConfigEntity> addonConfig = new ArrayList<>();

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WpProductSiteConfigEntity> siteConfigs = new ArrayList<>();

    @Enumerated(EnumType.ORDINAL)
    private ProductSaleType saleType;

    @Column
    private String sku;

    @Transient
    private WpProductEntity snapshot;

    @PostLoad
    public void createSnapshot() {
        this.snapshot = new WpProductEntity();
        this.snapshot.setStatus(status);
        this.snapshot.setWeight(weight);
        this.snapshot.setBuyPrice(buyPrice);
        this.snapshot.setStockQuantity(stockQuantity);
        this.snapshot.setSaleType(saleType);
        this.snapshot.setBrand(brand);
    }

    @Column
    private String stock_status;
    @Column
    private String type;
    @Column
    private boolean featured;
    @Column
    private String catalog_visibility;
    @Column
    private boolean manage_stock;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Dimensions dimensions;


}
