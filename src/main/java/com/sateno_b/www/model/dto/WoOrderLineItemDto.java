package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sateno_b.www.model.entity.data.PaoIdValue;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WoOrderLineItemDto {
        @JsonProperty("product_id")
        private Long productId;
        private String sku;
        @JsonProperty("name")
        private String productName;
        private int quantity;
        private BigDecimal subtotal;
        private BigDecimal total;
        @JsonProperty("meta_data")
        private List<WoPaoIdValueDto> paoIdValue;
    }
