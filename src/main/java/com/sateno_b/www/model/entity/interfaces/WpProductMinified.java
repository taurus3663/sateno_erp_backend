package com.sateno_b.www.model.entity.interfaces;

import com.sateno_b.www.model.enums.ProductSaleType;
import com.sateno_b.www.model.enums.ProductStatus;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public interface WpProductMinified {
    Long getId();
    String getSku();
    Integer getStockQuantity();
    ProductStatus getStatus();
    ProductSaleType getSaleType();
    BigDecimal getBuyPrice();
    BigDecimal getTransportPrice();

    // Коригираният SpEL израз:
    // 1. target.translations.![name] -> прави списък само от имената
    // 2. T(java.lang.String).join -> съединява ги с разделител
    @Value("#{T(java.lang.String).join(' | ', target.translations.![name])}")
    String getNames();

    @Value("#{target.brand != null ? target.brand.name : ''}")
    String getBrandName();

    @Value("#{target.images.?[isPrimary == true].size() > 0 ? " +
            "target.images.?[isPrimary == true][0].localSrc : " +
            "(target.images.size() > 0 ? target.images[0].localSrc : null)}")
    String getM_image();

    @Value("#{target.categories}")
    Set<CategoryMin> getCategories();

//    @Value("#{target.siteConfigs}")
//    List<SiteConfigMin> getSiteConfigs();

    interface CategoryMin {
        // Тук също използваме по-прост израз за името на категорията
        @Value("#{target.translations.size() > 0 ? target.translations[0].name : ''}")
        String getName();
    }

//    interface SiteConfigMin {
//        Long getId();
//        @Value("#{target.site.id}")
//        Long getSiteId();
//        @Value("#{target.site.name}")
//        String getSiteName();
//    }
}
