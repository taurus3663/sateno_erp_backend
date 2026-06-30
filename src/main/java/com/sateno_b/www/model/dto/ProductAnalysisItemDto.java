package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class ProductAnalysisItemDto {
    private String sku;
    private String productName;
    private int orderCount;
    private String rating; // "A", "B", "C", "D"
}
