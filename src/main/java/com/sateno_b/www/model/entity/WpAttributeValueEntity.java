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
@Table(name = "wp_attribute_value")
public class WpAttributeValueEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_type_id", nullable = false)
    private WpAttributeTypeEntity attributeType;

    @Column(nullable = false)
    private String slug;

    @OneToMany(mappedBy = "attributeValue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WpAttributeValueTranslationEntity> translations = new ArrayList<>();
}
