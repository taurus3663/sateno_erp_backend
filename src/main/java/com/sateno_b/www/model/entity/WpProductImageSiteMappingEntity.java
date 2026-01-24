package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wp_product_image_site_mapping")
public class WpProductImageSiteMappingEntity extends BaseEntity {

    @ManyToOne
    private WpProductImageEntity productImage;

    @ManyToOne
    @JoinColumn("site_id")
    private SiteEntity site;

    @Column
    private Long wpMediaId;

    @Column
    private String wpUrl;
}
