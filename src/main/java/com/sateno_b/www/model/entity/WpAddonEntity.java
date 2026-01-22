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
@Table(name = "wp_addon")
public class WpAddonEntity extends BaseEntity{

    @Column
    private String slug; // кратко име

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
    private List<WpAddonTranslationEntity> translations = new ArrayList<>();

//    @OneToMany(mappedBy = "group",  cascade = CascadeType.ALL)
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
            name = "wp_addon_addon_values",
            joinColumns = @JoinColumn(name = "addon_id"),
            inverseJoinColumns = @JoinColumn(name = "value_id")
    )
    private List<WpAddonValueEntity> values = new ArrayList<>();


}
