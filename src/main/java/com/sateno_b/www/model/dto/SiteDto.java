package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
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
    private EmailDto email;
    private String newOrderMessage;
    private Long changeStatusTimer;
    private Long secondOrderMessageTimer;
    private String secondOrderMessage;
    private Long thirdOrderMessageTimer;
    private String thirdOrderMessage;
    @JsonProperty("isDefault")
    private boolean isDefault = false;

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
