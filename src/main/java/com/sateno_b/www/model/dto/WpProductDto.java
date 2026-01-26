package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.WpBrandEntity;
import com.sateno_b.www.model.enums.ProductStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class WpProductDto {

    private Long id;
    private Integer stockQuantity;
    private String weight;
    private BigDecimal buyPrice;

    private WpBrandEntity brand;

    private List<WpProductTranslationDto> translations = new ArrayList<>();

    private List<WpProductAddonValuePriceDto> addonValuePrices = new ArrayList<>();

    private String names; // its for all language names only;
    private ProductStatus status_p;
    private String m_image;
}
