package com.sateno_b.www.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WpProductTranslationDto {

    private Long wpProductId;
    private String name;
    private String description;
    private String shortDescription;
    private String sku;
    private BigDecimal price;
    private BigDecimal regularPrice;
    private String slug;
    private LanguageDto language;
}
