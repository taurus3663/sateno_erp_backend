package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WpAddonValueDto2 {
    private Long id;
    private String slug;
//    private Map<String, Object> translations ; // За да съвпада с структурата в Angular
    private List<WpAddonValueTranslationDto> translations = new ArrayList<>();
    private String names; // Стрингът: "Зелен | Green | Verde"
}

