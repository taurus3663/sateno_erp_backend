package com.sateno_b.www.model.entity.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PaoIdValueValue {
    private String key;
    private String value;
    private Long id;
    private String rawValue;
    private String rawPrice;
    private String priceType;
}
