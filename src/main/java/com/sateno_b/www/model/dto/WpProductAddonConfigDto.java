package com.sateno_b.www.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WpProductAddonConfigDto {
    private Long id;
//    private Long siteId;       // За филтрирането в Angular
    private SiteDto site;
//    private Long addonValueId; // За връзката
    private String label;      // За визуализация (от превода)
    private BigDecimal priceModifier;
    private WpAddonValueDto2 addonValue;
}
