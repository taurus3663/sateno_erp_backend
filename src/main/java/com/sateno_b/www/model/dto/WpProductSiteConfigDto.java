package com.sateno_b.www.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WpProductSiteConfigDto {

    private Long id;
    private Long wpProductId;
    private String sku;
    private BigDecimal price;
    private BigDecimal regularPrice;
    private String slug;
    private SiteDto site;
//    private WpProductDto product;

}
