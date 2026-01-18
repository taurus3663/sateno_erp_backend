package com.sateno_b.www.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "language")
public class LanguageEntity extends BaseEntity {

    @Column
    private String name;

    @Column
    private String code;
}
