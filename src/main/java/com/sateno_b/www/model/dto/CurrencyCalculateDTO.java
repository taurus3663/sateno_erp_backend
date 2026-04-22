package com.sateno_b.www.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CurrencyCalculateDTO {

private BigDecimal fromAmount;
private String fromCode;
private String toCode;
private BigDecimal rsAmount;
}
