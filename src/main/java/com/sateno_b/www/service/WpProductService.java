package com.sateno_b.www.service;


import com.sateno_b.www.model.dto.WpProductDto;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WpProductService {

    private final SiteRepository siteRepository;
    private final RestClient restClient;
    private final WpProductRepository wpProductRepository;
    private final WpProductImageRepository wpProductImageRepository;
    private final WpProductAddonConfigRepository wpProductAddonConfigRepository;
    private final WpProductAddonValuePriceRepository wpProductAddonValuePriceRepository;
    private final WpProductImageSiteMappingRepository wpProductImageSiteMappingRepository;
    private final WpProductTranslationRepository wpProductTranslationRepository;

    private static final String PRODUCTS_URL = "/wp-json/wc/v3/products";


    @Transactional
    public void syncProductsToDB(Long siteId) {

    }

//    private List<WpProductDto> fetchAllProducts(SiteEntity site, String auth) {
//
//
//    }

}
