package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.enums.CourierType;
import lombok.Data;

@Data
public class CourierSettingsDto {

    private Long id;
    private CourierType courierType;
    private String name;
    private String username;
    private String password;
    private String apiKey;
    private String apiSecret;
    private SiteEntity site;
    private boolean active = true;
}
