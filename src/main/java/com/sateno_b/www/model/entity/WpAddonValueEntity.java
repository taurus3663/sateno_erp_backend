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
@Table(name = "wp_addon_value")
public class WpAddonValueEntity extends BaseEntity {

    @ManyToMany(mappedBy = "values")
    private List<WpAddonEntity> groups = new ArrayList<>();

    @Column
    private String slug; // green, xl, black

    @OneToMany(mappedBy = "addonValue", cascade = CascadeType.ALL)
    private List<WpAddonValueTranslationEntity> translations = new ArrayList<>();
}
