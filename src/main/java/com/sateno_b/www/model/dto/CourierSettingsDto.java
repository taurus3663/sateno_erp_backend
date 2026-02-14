package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.enums.CourierType;
import lombok.Data;

@Data
public class CourierSettingsDto {

    private Long id;
    private CourierType courierType;
    private CourierShipmentType courierShipmentType;
    private String name;
    private String username;
    private String password;
    private String apiKey;
    private String apiSecret;
    private SiteDto site;
    private boolean active = true;

//    @Column
    private Integer sortOrder;
//    @Column
    private Double freeShippingPriceMax;
//    @Column
    private Boolean autoShippingPrice = false;
//    @Column
    private Double fixedShippingPrice;
}
