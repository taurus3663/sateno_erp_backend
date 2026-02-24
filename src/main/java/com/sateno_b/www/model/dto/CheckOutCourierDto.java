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


    private boolean office = false;
    private Double officeFreeShippingPriceMax;
    private boolean officeFreeShippingPriceMaxBol = false;
    private boolean officeAutoShippingPrice = false;
    private Double officeFixedShippingPrice;

    private boolean address = false;
    private Double addressFreeShippingPriceMax;
    private boolean addressFreeShippingPriceMaxBol = false;
    private boolean addressAutoShippingPrice = false;
    private Double addressFixedShippingPrice;

    private boolean locker = false;
    private Double lockerFreeShippingPriceMax;
    private boolean lockerFreeShippingPriceMaxBol = false;
    private boolean lockerAutoShippingPrice = false;
    private Double lockerFixedShippingPrice;


}
