package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.LanguageEntity;
import com.sateno_b.www.model.entity.WpAddonValueEntity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Data
public class WpAddonValueTranslationDto {

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "value_id")
//    private WpAddonValueEntity addonValue;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "language_id")
    private LanguageDto language;

//    @Column(nullable = false)
    private String label; // зелен, green
}
