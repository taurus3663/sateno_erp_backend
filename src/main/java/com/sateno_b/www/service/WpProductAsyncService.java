package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.enums.ProductSaleType;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpCategorySiteMappingRepository;
import com.sateno_b.www.model.repository.WpProductImageSiteMappingRepository;
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
    private final WpProductImageSiteMappingRepository wpProductImageSiteMappingRepository;
    private final WpCategorySiteMappingRepository wpCategorySiteMappingRepository;


    @Transactional
    @Async
    public void updateProductOnSites(WpProductEntity product, Long lastEditedSiteId) throws InterruptedException {
        Thread.sleep(2000);
        product = wpProductRepository.findById(product.getId()).orElse(null);

//        List<SiteEntity> siteList = siteRepository.findAll();
        List<SiteEntity> siteList;

        // Ако lastEditedSiteId е null (т.е. нов продукт или глобална промяна), вземаме всички сайтове
        if (lastEditedSiteId == null) {
            siteList = siteRepository.findAll();
//            log.info("Стартиране на глобална синхронизация за SKU: {}", product.getSku());
        } else {
            // Ако е подаден конкретен сайт, обновяваме само него
            siteList = siteRepository.findById(lastEditedSiteId)
                    .map(List::of) // Превръщаме в списък с един елемент
                    .orElse(Collections.emptyList());
//            log.info("Стартиране на локална синхронизация за SKU: {} само за сайт ID: {}", product.getSku(), lastEditedSiteId);
        }


//        System.out.println(lastEditedSiteId);

        for (SiteEntity site : siteList) {
//            if(site.getId().equals(sourceSiteId) || site.getUrl().equals("sateno.bg")) continue;
//            System.out.println(site.toString());
            try {
                String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

                var searchResponse = restClient.get()
                        .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products?sku=" + product.getSku())
                        .header("Authorization", "Basic " + auth)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                boolean isNewProduct = false;
                if (searchResponse == null || searchResponse.isEmpty()) {
                    log.warn("Продукт с SKU {} не е намерен в сайта {}", product.getSku(), site.getUrl());
                    isNewProduct = true;
                }


                Map<String, Object> updateBody = new HashMap<>();
                updateBody.put("stock_quantity", product.getStockQuantity());
                updateBody.put("manage_stock", product.getSaleType() != ProductSaleType.UNLIMITED);
                updateBody.put("backorders", product.getSaleType() == ProductSaleType.UNLIMITED? "yes": "no");
                updateBody.put("backorders_allowed", product.getSaleType() == ProductSaleType.UNLIMITED);
                updateBody.put("weight", product.getWeight());
                updateBody.put("status", product.getStatus().getValue());

//                DESCRIPTION
                for (WpProductTranslationEntity translation : product.getTranslations()) {
                    if(translation.getLanguage() == site.getLanguage()) {
                        updateBody.put("short_description", translation.getShortDescription());
                        updateBody.put("description", translation.getDescription());
                        updateBody.put("name", translation.getName());
                        break;
                    }
                }



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
//                List<Map<String, Object>> categoriesList = new ArrayList<>();
//                for (WpCategoryEntity category : product.getCategories()) {
//                    var searchResponseCategory = restClient.get()
//                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories?slug=" + category.getSlug())
//                            .header("Authorization", "Basic " + auth)
//                            .retrieve()
//                            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
//
//                    if (searchResponseCategory != null && !searchResponseCategory.isEmpty()) {
//                        Long wpCategoryId = Long.valueOf(searchResponseCategory.get(0).get("id").toString());
//
//                        // 2. За всяка категория създаваме НОВ обект Map
//                        Map<String, Object> categoryItem = new HashMap<>();
//                        categoryItem.put("id", wpCategoryId);
//
//                        // 3. Добавяме го в списъка
//                        categoriesList.add(categoryItem);
//                    }
//                }
//                if (!categoriesList.isEmpty()) {
//                    updateBody.put("categories", categoriesList);
//                }

//                IMAGES

                //                CATEGORIES
                List<Map<String, Object>> categoriesList = new ArrayList<>();
                if (product.getCategories() != null) {
                    for (WpCategoryEntity category : product.getCategories()) {

                        // 1. Търсим мапинга в нашата база данни за този конкретен сайт
                        // Използваме репозиторито, което вече трябва да имаш инжектирано
                        Optional<WpCategorySiteMappingEntity> mappingOpt =
                                wpCategorySiteMappingRepository
                                .findByWpCategoryAndSite(category, site);

                        if (mappingOpt.isPresent()) {
                            // Ако имаме запис, вземаме директно wpId
                            Long wpCategoryId = mappingOpt.get().getWpId();

                            Map<String, Object> categoryItem = new HashMap<>();
                            categoryItem.put("id", wpCategoryId);
                            categoriesList.add(categoryItem);
                        } else {
                            // 2. ФАЛБЕК: Ако по някаква причина нямаме мапинг, търсим по slug в API-то
                            // (Това е твоята стара логика, но като резервен вариант)
                            log.warn("Няма мапинг за категория {} в сайт {}. Търсене чрез API...", category.getSlug(), site.getUrl());

                            var searchResponseCategory = restClient.get()
                                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories?slug=" + category.getSlug())
                                    .header("Authorization", "Basic " + auth)
                                    .retrieve()
                                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                            if (searchResponseCategory != null && !searchResponseCategory.isEmpty()) {
                                Long wpCategoryId = Long.valueOf(searchResponseCategory.get(0).get("id").toString());
                                Map<String, Object> categoryItem = new HashMap<>();
                                categoryItem.put("id", wpCategoryId);
                                categoriesList.add(categoryItem);

                                // Опционално: Създай мапинг тук, за да не търсиш следващия път
                            }
                        }
                    }
                }

                // Винаги подаваме списъка (дори и празен, ако искаме да изчистим категориите в WP)
                updateBody.put("categories", categoriesList);


                List<Map<String, Object>> imageList = new ArrayList<>();
                if (product.getImages() != null) {
                    for (WpProductImageEntity imgEntity : product.getImages()) {

                        Optional<WpProductImageSiteMappingEntity> mappingOpt = wpProductImageSiteMappingRepository
                                .findByProductImageIdAndSite(imgEntity.getId(), site);

                        Long wpMediaId = null;
                        if (mappingOpt.isPresent()) {
                            wpMediaId = mappingOpt.get().getWpMediaId();
                        }
                        else {
                            wpMediaId = imageToWordPress.uploadImageToWordPress(site, imgEntity.getLocalSrc());
                            if (wpMediaId != null) {
                                WpProductImageSiteMappingEntity wpProductImageSiteMappingEntity = new WpProductImageSiteMappingEntity();
                                wpProductImageSiteMappingEntity.setWpMediaId(wpMediaId);
                                wpProductImageSiteMappingEntity.setSite(site);
                                wpProductImageSiteMappingEntity.setProductImage(imgEntity);
//                                wpProductImageSiteMappingEntity.setWpUrl();
                                wpProductImageSiteMappingRepository.save(wpProductImageSiteMappingEntity);
                            }

                        }
                        if (wpMediaId != null) {
                            Map<String, Object> imgMap = new HashMap<>();
                            imgMap.put("id", wpMediaId);
                            imageList.add(imgMap);
                        }
                    }
                }
                if(!imageList.isEmpty()) {
                    updateBody.put("images", imageList);
                }


                // --- ADDONS LOGIC (Генериране на структури за WooCommerce Product Add-ons) ---
                List<Map<String, Object>> wooAddons = new ArrayList<>();

// Проверяваме дали в ERP има налични конфигурации за адони
                if (product.getAddonConfig() != null && !product.getAddonConfig().isEmpty()) {

                    // 1. Сортираме адоните по ID (ред на добавяне), за да запазим подредбата от БД
                    List<WpProductAddonConfigEntity> sortedConfigs = product.getAddonConfig().stream()
                            .sorted(Comparator.comparing(BaseEntity::getId))
                            .collect(Collectors.toList());

                    // 2. Групираме ги по име на групата (напр. "Размер", "Цвят")
                    Map<String, List<WpProductAddonConfigEntity>> groupedAddons = new LinkedHashMap<>();

                    for (WpProductAddonConfigEntity conf : sortedConfigs) {
                        // Вземаме първата група, към която принадлежи стойността
                        WpAddonEntity group = conf.getAddonValue().getGroups().get(0);

                        // Вземаме превода на името на групата спрямо езика на сайта
                        String groupName = group.getTranslations().stream()
                                .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
                                .map(WpAddonTranslationEntity::getName)
                                .findFirst()
                                .orElseGet(() ->
                                        group.getTranslations().stream()
                                                .filter(t -> t.getLanguage().getCode().equals("bg"))
                                                .map(WpAddonTranslationEntity::getName)
                                                .findFirst()
                                                .orElse(group.getSlug())
                                );

                        groupedAddons.computeIfAbsent(groupName, k -> new ArrayList<>()).add(conf);
                    }

                    // 3. Превръщаме групираните данни във формат, който WooCommerce разбира
                    int groupPosition = 0;
                    for (Map.Entry<String, List<WpProductAddonConfigEntity>> entry : groupedAddons.entrySet()) {
                        Map<String, Object> addonGroupMap = new HashMap<>();

                        addonGroupMap.put("name", entry.getKey());
                        addonGroupMap.put("type", "multiple_choice");
                        addonGroupMap.put("display", "radiobutton");
                        addonGroupMap.put("position", groupPosition++);
                        addonGroupMap.put("required", 1);
                        addonGroupMap.put("title_format", "label");
                        addonGroupMap.put("adjust_price", 1);

                        List<Map<String, Object>> options = new ArrayList<>();
                        int optionPosition = 0;

                        for (WpProductAddonConfigEntity config : entry.getValue()) {
                            // Вземаме превода на етикета на самата стойност (напр. "XL", "Червен")
                            String label = config.getAddonValue().getTranslations().stream()
                                    .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
                                    .map(WpAddonValueTranslationEntity::getLabel)
                                    .findFirst()
                                    .orElseGet(() ->
                                            config.getAddonValue().getTranslations().stream()
                                                    .filter(t -> t.getLanguage().getCode().equals("bg"))
                                                    .map(WpAddonValueTranslationEntity::getLabel)
                                                    .findFirst()
                                                    .orElse(config.getAddonValue().getSlug())
                                    );

                            Map<String, Object> option = new HashMap<>();
                            option.put("label", label);
                            // Ако цената е 0, пращаме празен стринг, за да не се показва "+0.00" в сайта
                            option.put("price", config.getPriceModifier().compareTo(BigDecimal.ZERO) > 0
                                    ? config.getPriceModifier().toString() : "");
                            option.put("price_type", "quantity_based");
                            option.put("position", optionPosition++);
                            option.put("image", "");
                            option.put("visibility", 1);

                            options.add(option);
                        }

                        addonGroupMap.put("options", options);
                        wooAddons.add(addonGroupMap);
                    }
                }

// ВАЖНО: Винаги добавяме ключа в обекта за ъпдейт.
// Ако wooAddons е празен списък [], WooCommerce ще изтрие всички стари адони от продукта.
                updateBody.put("addons", wooAddons);




                if(isNewProduct) {
                    updateBody.put("sku", product.getSku());

                    restClient.post()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products")
                            .header("Authorization", "Basic " + auth)
                            .body(updateBody)
                            .retrieve()
                            .toBodilessEntity();
                    log.info("Успешно създаден нов продукт с SKU {} в сайт {}", product.getSku(), site.getUrl());
                }
                else {
                    Integer wpId = (Integer) searchResponse.get(0).get("id");
                                        restClient.patch()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/" + wpId)
                            .header("Authorization", "Basic " + auth)
                            .body(updateBody)
                            .retrieve()
                            .toBodilessEntity();


                    Set<Long> erpWpMediaIds = imageList.stream()
                            .map(img -> Long.valueOf(img.get("id").toString()))
                            .collect(Collectors.toSet());

                    List<Map<String, Object>> wpImagesFromSearch = (List<Map<String, Object>>) searchResponse.get(0).get("images");

                    if (wpImagesFromSearch != null) {
                        // Намираме кои ID-та съществуват в WP, но ги няма в нашия нов списък от ERP
                        Set<Long> idsToDeleteFromMediaLibrary = wpImagesFromSearch.stream()
                                .map(img -> Long.valueOf(img.get("id").toString()))
                                .filter(id -> !erpWpMediaIds.contains(id)) // Ако го няма в ERP списъка -> за триене
                                .collect(Collectors.toSet());

                        // 3. Физическо триене от Media Library
                        if (!idsToDeleteFromMediaLibrary.isEmpty()) {
                            imageToWordPress.deleteMediaOneByOne(site, idsToDeleteFromMediaLibrary, auth);
                            log.info("Изтрити са {} излишни медийни файла от сайт {}", idsToDeleteFromMediaLibrary.size(), site.getUrl());
                        }
                    }


//                    // Взимаме WordPress ID-то от първия намерен резултат
//                    Integer wpId = (Integer) searchResponse.get(0).get("id");
//
//                    restClient.patch()
//                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/" + wpId)
//                            .header("Authorization", "Basic " + auth)
//                            .body(updateBody)
//                            .retrieve()
//                            .toBodilessEntity();
////                System.out.println(wpId);
////                System.out.println(authorization.getBody());
//                    log.info("Успешно обновен sale_price за SKU {}", product.getSku());
//
//                    Map<String, Object> wpProduct = searchResponse.get(0);
//                    List<Map<String, Object>> currentWpImages = (List<Map<String, Object>>) wpProduct.get("images");
//                    imageToWordPress.deleteMediaOneByOne(
//                            site,
//                            currentWpImages.stream()
//                                    .map(e -> Long.valueOf(e.get("id").toString())) // Безопасно конвертиране
//                                    .collect(Collectors.toSet()),
//                            auth
//                    );
                }
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
