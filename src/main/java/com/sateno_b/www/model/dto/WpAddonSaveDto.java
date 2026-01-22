package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class WpAddonSaveDto {
    private Long id;
    private String slug;
    private String name;      // Името от сигнала currentTranslationName()
    private Long langId;      // От избрания selectedLanguage.id
    private List<Long> valueIds; // Всички ID-та от дясната страна на PickList
}
