package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WoPaoIdValueValueDto {
    private String key;
    private String value;
    private Long id;
    @JsonProperty("raw_value")
    private String rawValue;
    @JsonProperty("raw_price")
    private String rawPrice;
    @JsonProperty("price_type")
    private String priceType;
}
