package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wp_category")
public class WpCategoryEntity extends BaseEntity {

    @Column()
    private String slug;

//    @Column
//    private Long parentWpId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private WpCategoryEntity parent;

    @BatchSize(size = 30) // Изключително важно за производителността!
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "wp_category_id")
    private List<WpCategoryTranslationEntity> translations = new ArrayList<>();




}
