package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WpAddonResponseDto {

    private Long id;
    private String slug;
    // Map: "bg" -> "Цвят", "en" -> "Color"
    private Map<String, String> translations;
    private String names;
}
