package com.sateno_b.www.model.entity.data;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

//@JsonTypeInfo(
//        use = JsonTypeInfo.Id.NAME,
//        include = JsonTypeInfo.As.EXTERNAL_PROPERTY, // Тъй като типът е в основното ентити
//        property = "courierType"
//)
//@JsonSubTypes({
//        @JsonSubTypes.Type(value = EcontConfig.class, name = "ECONT"),
//        @JsonSubTypes.Type(value = SpeedyConfig.class, name = "SPEEDY"),
//        @JsonSubTypes.Type(value = BoxNowConfig.class, name = "BOX_NOW")
//})
@Data
public class CourierConfig {

    private String agentName;
    private String phoneNumber;
    private String mail;
    private String companyName;
    private String city;
    private String postalCode;
    private String address;
}

