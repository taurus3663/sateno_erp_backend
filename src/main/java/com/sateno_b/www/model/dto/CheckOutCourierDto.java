package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.enums.CourierType;
import lombok.Data;

@Data
public class CheckOutCourierDto {

    private CourierType courierType;
    private CourierShipmentType courierShipmentType;

    private String fixedShippingPrice;
    private String freeShippingPriceMax;
    private Integer sortOrder;
    private boolean active;

}
