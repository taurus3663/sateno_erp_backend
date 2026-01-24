package com.sateno_b.www.model.entity;

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
    private ProductUnit unit;

    @Column
    private boolean status;

    @Column
    private String weight;

    @Column
    private BigDecimal buyPrice;

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

    @ManyToMany
    @JoinTable(
            name = "wp_product_addon_mapping",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "addon_id")
    )
    private Set<WpAddonEntity> addons = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "wp_product_addon_value_mapping",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "addon_value_id")
    )
    private Set<WpAddonValueEntity> addonValues = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WpProductAddonValuePriceEntity> addonValuePrices = new ArrayList<>();

}
