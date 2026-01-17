package com.sateno_b.www.model.dto;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CurrencyDto {

    private Long id;
    private String name; // Български лев, Евро
    private String symbol; // лв., €, $
    private String code;
    private int decimalPlaces = 2; // Колко знака след запетаята ползва (обикновено 2)
}
