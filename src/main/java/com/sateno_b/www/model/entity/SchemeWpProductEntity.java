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
@Table(name = "scheme_pr")
public class SchemeWpProductEntity extends BaseEntity {

    @Column
    String name;

    @Column(columnDefinition = "text")
    String description;

}
