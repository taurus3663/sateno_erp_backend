package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class ProductTranslateContentDTO {
    private WpProductTranslationDto item;
    private Long type;
    private Long productId;
}
