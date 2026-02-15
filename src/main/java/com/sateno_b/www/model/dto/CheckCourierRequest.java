package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class CheckCourierRequest {

    private String site;
    private String cart_total;
    private double cart_weight;
    private String items_count;
    private List<CheckOutCourierItemsDto> items;
    private String currency;
}
