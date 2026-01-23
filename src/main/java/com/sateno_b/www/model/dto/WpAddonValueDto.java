package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WpAddonValueDto {
    private Long id;
    private String slug;
    private Map<String, Object> translations; // За да съвпада с структурата в Angular
    private String names; // Стрингът: "Зелен | Green | Verde"
}
