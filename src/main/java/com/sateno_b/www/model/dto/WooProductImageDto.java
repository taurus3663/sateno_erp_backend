package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class WooProductImageDto {

    private Long id; //id of img on wp site WpMediaId
    private String src; // url link of the img wpUrl
    private String name; // name of img

}
