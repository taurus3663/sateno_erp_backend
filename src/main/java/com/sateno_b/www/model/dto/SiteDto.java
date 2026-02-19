package com.sateno_b.www.model.dto;

import lombok.*;

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

    public void setUrl(String url) {
        if (url != null) {
            if (url.startsWith("https://")) {
                url = url.replace("https://", "");
            } else if (url.startsWith("http://")) {
                url = url.replace("http://", "");
            }
        }
        this.url = url;
    }
}
