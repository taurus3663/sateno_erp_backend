package com.sateno_b.www.model.entity.data;

import com.sateno_b.www.model.dto.WoOrderLineItemImageDto;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderLineItem {
    private Long productId;
    private String sku;
    private String productName;
    private int quantity;
    private BigDecimal price;
    private BigDecimal totalPrice;
    private List<PaoIdValue> paoIdValue;
    private WoOrderLineItemImageDto image;
}
