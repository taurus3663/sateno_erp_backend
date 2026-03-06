package com.sateno_b.www.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WpCategoryDetailDto {
    private Long id;
    private String slug;
    private Long parentId;
    private String parentName; // Името на родителя (за избрания текущ език)
    private String name;

    // Map<LanguageCode, Name> -> напр. {"bg": "Обувки", "en": "Shoes"}
    private Map<String, String> translations;
}
