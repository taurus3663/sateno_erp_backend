package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.enums.CourierType;
import lombok.Data;

@Data
public class CheckOutCourierDto {

    private CourierType courierType;
    private CourierShipmentType courierShipmentType;

    private Double fixedShippingPrice;
    private Double freeShippingPriceMax;
    private Integer sortOrder;
    private boolean active;
    private boolean autoShippingPrice;

}
