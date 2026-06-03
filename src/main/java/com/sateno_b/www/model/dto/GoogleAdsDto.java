package com.sateno_b.www.model.dto;


import lombok.Data;

@Data
public class GoogleAdsDto {

    private Long id;
    private String name;
    private boolean active;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String loginCustomerId;
}
