package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SiteDto {
    private Long id;
    private String name;
    private String url;
    private String consumerKey;
    private String consumerSecret;
    private CurrencyDto currency;
    private LanguageDto language;
    private boolean active = false;
    private String orderCreateApiKey;

    private List<CourierSettingsDto> couriers = new ArrayList<>();
}
