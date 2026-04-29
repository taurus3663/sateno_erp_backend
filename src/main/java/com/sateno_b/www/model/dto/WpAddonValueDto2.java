package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class WpAddonValueDto2 {
    private Long id;
    private String slug;
//    private Map<String, Object> translations2 = new HashMap<>(); // За да съвпада с структурата в Angular
    private List<WpAddonValueTranslationDto> translations = new ArrayList<>();
    private String names; // Стрингът: "Зелен | Green | Verde"
}

