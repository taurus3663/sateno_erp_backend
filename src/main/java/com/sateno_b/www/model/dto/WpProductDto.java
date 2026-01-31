package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.enums.ProductStatus;
import com.sateno_b.www.model.enums.ProductUnit;
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

    private WpBrandDto brand;

    private List<WpProductTranslationDto> translations = new ArrayList<>();
    private List<WpProductAddonConfigDto> addonConfigs = new ArrayList<>();
    private List<WpCategoryDetailDto> categories = new ArrayList<>();
    private List<WpProductSiteConfigDto> siteConfig = new ArrayList<>();

    private ProductStatus status;

    private String names; // its for all language names only;
    private ProductStatus status_p;
    private String m_image;
    private List<WpProductImageDto> images = new ArrayList<>();

    private ProductUnit unit;
}
