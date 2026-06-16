package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WpAttributeTypeDto {
    private Long id;
    private String slug;
    private boolean multipleValues;
    private Map<String, Object> translations;
    private String label;
    private List<WpAttributeValueDto> values;
}
