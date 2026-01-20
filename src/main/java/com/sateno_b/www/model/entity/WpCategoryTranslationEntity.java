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
@Table(name = "wp_category_name_translation")
public class WpCategoryTranslationEntity extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "language_id")
    private LanguageEntity language;

    @Column
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wp_category_id")
    private WpCategoryEntity wpCategory; // Това е полето, към което сочи mappedBy
}
