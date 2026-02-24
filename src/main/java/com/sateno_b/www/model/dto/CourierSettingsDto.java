package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.data.CourierConfig;
import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.enums.CourierType;
import lombok.Data;

@Data
public class CourierSettingsDto {

    private Long id;
    private CourierType courierType;
    private boolean office = false;
    private boolean address = false;
    private boolean locker = false;
//    private CourierShipmentType courierShipmentType;
    private String name;
    private String username;
    private String password;
    private String apiKey;
    private String apiSecret;
    private SiteDto site;
    private boolean active = false;

    private Integer sortOrder;
    private Double freeShippingPriceMax;
    private Boolean freeShippingPriceMaxBol = false;
    private Boolean autoShippingPrice = false;
    private Double fixedShippingPrice;
    private boolean defaultCourier = false;
    private CourierConfig config;
}
