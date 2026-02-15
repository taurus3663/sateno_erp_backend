package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class CheckOutCourierItemsDto {

    private Long id;
    private String name;
    private Double price;
    private Long quantity;
    private String sku;
    private Long weight;
}
