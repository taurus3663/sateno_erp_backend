package com.sateno_b.www.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
