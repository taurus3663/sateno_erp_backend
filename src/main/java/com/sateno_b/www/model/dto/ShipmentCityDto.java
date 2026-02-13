package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class ShipmentCityDto {
    private Long id;
    private String name;
    private String nameEn;
    private String postCode;

    private String type;
    private String typeEn;
    private String municipality;
    private String municipalityEn;
    private String region;
    private String regionEn;


}
