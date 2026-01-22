package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class WpAddonDetailDto {
    private Long id;
    private String slug;
    private List<WpAddonValueDto> availableValues;
    private List<WpAddonValueDto> selectedValues;
}
