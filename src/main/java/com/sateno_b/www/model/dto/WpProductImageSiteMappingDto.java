package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class WpProductImageSiteMappingDto {

    private Long id;
    private Long wpMediaId;
    private String wpUrl;
    private Long siteId;
}
