package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WpAttributeValueDto {
    private Long id;
    private Long typeId;
    private String slug;
    private Map<String, Object> translations;
    private String label;
}
