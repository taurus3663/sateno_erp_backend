package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wp_brand")
public class WpBrandEntity extends BaseEntity {

    @Column
    private String name;

    @Column
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String imageUrl;

    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WpBrandWpIdEntity> wpId;

    @OneToMany(mappedBy = "brand")
    private List<WpProductEntity> products;

}
