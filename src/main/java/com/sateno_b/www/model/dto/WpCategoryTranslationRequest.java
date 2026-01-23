package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class WpCategoryTranslationRequest {
    private Long categoryId;
    private Long languageId;
    private String name;
    private Long parentId;
}
