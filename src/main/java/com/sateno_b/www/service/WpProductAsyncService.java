package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.enums.ProductSaleType;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpProductRepository;
import com.sateno_b.www.shared.ImageToWordPress;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class WpProductAsyncService {

    private final SiteRepository siteRepository;
    private final RestClient restClient;
    private final ImageToWordPress imageToWordPress;
    private final WpProductRepository wpProductRepository;


    @Transactional
    @Async
    public void updateProductOnSites(WpProductEntity product, Long sourceSiteId) {

        product = wpProductRepository.findById(product.getId()).orElse(null);

        List<SiteEntity> siteList = siteRepository.findAll();
        for (SiteEntity site : siteList) {
            if(site.getId().equals(sourceSiteId) || site.getUrl().equals("sateno.bg")) continue;

            try {
                String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

                var searchResponse = restClient.get()
                        .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products?sku=" + product.getSku())
                        .header("Authorization", "Basic " + auth)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                if (searchResponse == null || searchResponse.isEmpty()) {
                    log.warn("Продукт с SKU {} не е намерен в сайта {}", product.getSku(), site.getUrl());
                    return;
                }


                Map<String, Object> updateBody = new HashMap<>();
                updateBody.put("stock_quantity", product.getStockQuantity());
                updateBody.put("manage_stock", product.getSaleType() != ProductSaleType.UNLIMITED);
                updateBody.put("backorders", product.getSaleType() == ProductSaleType.UNLIMITED? "yes": "no");
                updateBody.put("backorders_allowed", product.getSaleType() == ProductSaleType.UNLIMITED);
                updateBody.put("weight", product.getWeight());
                updateBody.put("status", product.getStatus().getValue());

//                DESCRIPTION
//                for (WpProductTranslationEntity translation : product.getTranslations()) {
//                    if(translation.getLanguage() == site.getLanguage()) {
//                        updateBody.put("short_description", translation.getShortDescription());
//                        updateBody.put("description", translation.getDescription());
//                    }
//                }

//                PRICE
                for (WpProductSiteConfigEntity siteConfig : product.getSiteConfigs()) {
                    if(siteConfig.getSite() == site) {
                        updateBody.put("price", siteConfig.getRegularPrice().toString());
                        updateBody.put("regular_price", siteConfig.getRegularPrice().toString());
                        String salePriceStr = (siteConfig.getPrice() != null && siteConfig.getPrice().compareTo(BigDecimal.ONE) >= 0)
                                ? siteConfig.getPrice().toString()
                                : "";

                        updateBody.put("sale_price", salePriceStr);
                    }
                }

//                BRAND
                if(product.getBrand() != null) {
                    var searchResponseBrand = restClient.get()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/brands?slug=" + product.getBrand().getSlug())
                            .header("Authorization", "Basic " + auth)
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                    Long wpBrandId = null;
                    if (searchResponseBrand != null && !searchResponseBrand.isEmpty()) {
                        wpBrandId = Long.valueOf(searchResponseBrand.get(0).get("id").toString());
                    }

                    if(wpBrandId != null) {

                        Map<String, Object> brandObject = new HashMap<>();
                        brandObject.put("id", wpBrandId);

                        updateBody.put("brands", List.of(brandObject));
                    }
                }

//                CATEGORIES
                List<Map<String, Object>> categoriesList = new ArrayList<>();
                for (WpCategoryEntity category : product.getCategories()) {
                    var searchResponseCategory = restClient.get()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories?slug=" + category.getSlug())
                            .header("Authorization", "Basic " + auth)
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                    if (searchResponseCategory != null && !searchResponseCategory.isEmpty()) {
                        Long wpCategoryId = Long.valueOf(searchResponseCategory.get(0).get("id").toString());

                        // 2. За всяка категория създаваме НОВ обект Map
                        Map<String, Object> categoryItem = new HashMap<>();
                        categoryItem.put("id", wpCategoryId);

                        // 3. Добавяме го в списъка
                        categoriesList.add(categoryItem);
                    }
                }
                if (!categoriesList.isEmpty()) {
                    updateBody.put("categories", categoriesList);
                }

//                IMAGES
                List<Map<String, Object>> imageList = new ArrayList<>();
                if (product.getImages() != null) {
                    for (WpProductImageEntity imgEntity : product.getImages()) {
                        // Викаме твоя метод за качване
                        Long wpMediaId = imageToWordPress.uploadImageToWordPress(site, imgEntity.getLocalSrc());
                        if (wpMediaId != null) {
                            Map<String, Object> imgMap = new HashMap<>();
                            imgMap.put("id", wpMediaId); // Свързваме чрез ID в Media Library
                            imageList.add(imgMap);
                        }
                    }
                }
                if(!imageList.isEmpty()) {
                    updateBody.put("images", imageList);
                }






                // Взимаме WordPress ID-то от първия намерен резултат
                Integer wpId = (Integer) searchResponse.get(0).get("id");

                restClient.patch()
                        .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/" + wpId)
                        .header("Authorization", "Basic " + auth)
                        .body(updateBody)
                        .retrieve()
                        .toBodilessEntity();
//                System.out.println(wpId);
//                System.out.println(authorization.getBody());
                log.info("Успешно обновен sale_price за SKU {}", product.getSku());


                Map<String, Object> wpProduct = searchResponse.get(0);
                List<Map<String, Object>> currentWpImages = (List<Map<String, Object>>) wpProduct.get("images");
                imageToWordPress.deleteMediaOneByOne(
                        site,
                        currentWpImages.stream()
                                .map(e -> Long.valueOf(e.get("id").toString())) // Безопасно конвертиране
                                .collect(Collectors.toSet()),
                        auth
                );
//                if(currentWpImages != null && !currentWpImages.isEmpty()) {
//                    for (Map<String, Object> image : currentWpImages) {
//                        Integer mediaId = (Integer) image.get("id");
//
//                        if (mediaId != null && mediaId > 0) {
//                            try {
//                                // ФИЗИЧЕСКО ИЗТРИВАНЕ ОТ MEDIA LIBRARY (за да няма кеш и боклук)
//                                // Използваме стандартното WordPress API (wp/v2/media)
//                                restClient.delete()
//                                        .uri(site.getUrlWithHttps() + "/wp-json/wp/v2/media/" + mediaId + "?force=true")
//                                        .header("Authorization", "Basic " + auth)
//                                        .retrieve()
//                                        .toBodilessEntity();
//
//                                log.info("Изтрита медия ID {} от сайт {}", mediaId, site.getUrl());
//                            } catch (Exception e) {
//                                // Често се случва снимката вече да е изтрита ръчно или от друг процес
//                                log.warn("Грешка при триене на медия {}: {}", mediaId, e.getMessage());
//                            }
//                        }
//
//                    }
//                }





            } catch (Exception e) {
                log.error("Грешка при обновяване на сайт {}: {}", site.getUrl(), e.getMessage());
            }



        }


    }

}
