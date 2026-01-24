package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wp_product_image")
public class WpProductImageEntity extends BaseEntity {

    @Column
    private String localSrc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private WpProductEntity product;

    @OneToMany(mappedBy = "productImage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WpProductImageSiteMappingEntity> siteMappings = new ArrayList<>();
}
