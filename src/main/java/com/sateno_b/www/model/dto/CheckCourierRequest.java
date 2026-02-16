package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.enums.CourierType;
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

    private String targetId;
    private String cityName;
    private String postcode;
    private CourierType courierType;
    private CourierShipmentType courierShipmentType;

    public double getCart_weight() {
        return cart_weight == 0? 0.5 : cart_weight;
    }
}
