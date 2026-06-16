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
@Table(name = "wp_attribute_type")
public class WpAttributeTypeEntity extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(nullable = false)
    private boolean multipleValues = false;

    @OneToMany(mappedBy = "attributeType", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WpAttributeTypeTranslationEntity> translations = new ArrayList<>();

    @OneToMany(mappedBy = "attributeType", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WpAttributeValueEntity> values = new ArrayList<>();
}
