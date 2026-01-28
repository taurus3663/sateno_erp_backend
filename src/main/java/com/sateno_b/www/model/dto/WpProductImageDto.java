package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WpProductImageDto {

    private Long id;
    private String localSrc;

    private List<WpProductImageSiteMappingDto> siteMappings = new ArrayList<>();

    // Специфични полета за логиката с качване:
    @JsonProperty("isTemp")
    private boolean isTemp;   // Идва като true от Angular за нови снимки
    private String tempName;  // Идва като "temp_uuid.jpg" от Angular
}
