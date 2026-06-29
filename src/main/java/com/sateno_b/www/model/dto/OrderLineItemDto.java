package com.sateno_b.www.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderLineItemDto {
    private Long productId;
    private String sku;
    private String productName;
    private int quantity;
    private BigDecimal price;
    private BigDecimal totalPrice;
    private List<PaoIdValueDto> paoIdValue;
    private WoOrderLineItemImageDto image;

    private Long orderId;
    private Long wpOrderId;

    private String weight;
    private DimensionsDto dimensions;

    /** totalPrice > 0 → totalPrice; иначе price*qty + addon rawPrices (за карта-платени редове) */
    private BigDecimal effectiveTotalPrice;
}
