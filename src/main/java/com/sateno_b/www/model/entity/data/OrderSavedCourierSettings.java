package com.sateno_b.www.model.entity.data;

import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.enums.CourierType;
import lombok.Data;

@Data
public class OrderSavedCourierSettings {

    private Long courierId;
    private CourierType courierType;
    private CourierShipmentType courierShipmentType;

    private Object city;
    private Object office;

    private String street;
    private Double weight;
    private Integer packCount;
    private Boolean fiscalReceipt;
    private String boxNowSize;

}
