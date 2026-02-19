package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.enums.BoxNowPacketSize;
import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.enums.CourierType;
import lombok.Data;

@Data
public class CreateLabelDto {

    private Long id;
    private Long wpOrderId;
    private Long packCount;
    private Double weight;
    private Double length;
    private Double width;
    private Double height;
    private CourierType courierType;
    private CourierShipmentType courierShipmentType;
    private Long courierId;
    private Office office;
    private City city;
    private String street;
    private BoxNowPacketSize boxNowPacketSize;

    @Data
    public static class Office {
        private String address;
        private Long id;
    }

    @Data
    public static class City {
        private Long id;
        private String name;
        private String postalCode;
    }

}
