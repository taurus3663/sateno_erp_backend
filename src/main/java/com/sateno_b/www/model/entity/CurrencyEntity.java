package com.sateno_b.www.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "currency")
public class CurrencyEntity extends BaseEntity {

    @Column(nullable = false)
    private String name; // Български лев, Евро

    @Column(nullable = false)
    private String symbol; // лв., €, $

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private int decimalPlaces = 2; // Колко знака след запетаята ползва (обикновено 2)
}
