package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.WpProductImageDto;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.enums.ProductSaleType;
import com.sateno_b.www.model.repository.*;
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
    private final WpProductTranslationRepository wpProductTranslationRepository;
    private final ChatGptService chatGptService;
    private final CurrencyService currencyService;
    private final WpProductSiteConfigRepository wpProductSiteConfigRepository;
    private final CurrencyRepository currencyRepository;

    @Transactional
    @Async
    public void updateProductOnSites(WpProductEntity product, Long lastEditedSiteId) throws InterruptedException {
        updateProductOnSites(product, lastEditedSiteId, null);
    }

    @Transactional
    @Async
    public void updateProductOnSites(WpProductEntity product, Long lastEditedSiteId, List<WpProductImageDto> order) throws InterruptedException {
        Thread.sleep(2000);
        product = wpProductRepository.findById(product.getId()).orElse(null);
        if(product == null) return;


        // --- КРИТИЧНА ЧАСТ: FORCE INITIALIZATION ---
        if (product.getAddonConfig() != null) {
            product.getAddonConfig().forEach(config -> {
                WpAddonValueEntity val = config.getAddonValue();
                val.getTranslations().size();

                if (val.getGroups() != null && !val.getGroups().isEmpty()) {
                    val.getGroups().forEach(group -> group.getTranslations().size());
                }
            });
        }
        // --- КРАЙ НА ИНИЦИАЛИЗАЦИЯТА ---


        List<SiteEntity> siteList;

        if (lastEditedSiteId == null) {
            siteList = siteRepository.findAll();
        } else {
            siteList = siteRepository.findById(lastEditedSiteId)
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }

        for (SiteEntity site : siteList) {
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
                List<Map<String, Object>> currentMeta = new ArrayList<>();
                if(!isNewProduct) {
                    currentMeta = (List<Map<String, Object>>) searchResponse.get(0).get("meta_data");
                }

                Map<String, Object> updateBody = new HashMap<>();
                updateBody.put("stock_quantity", product.getStockQuantity());
                updateBody.put("manage_stock", product.getSaleType() != ProductSaleType.UNLIMITED);
                updateBody.put("backorders", product.getSaleType() == ProductSaleType.UNLIMITED? "yes": "no");
                updateBody.put("backorders_allowed", product.getSaleType() == ProductSaleType.UNLIMITED);
                updateBody.put("weight", product.getWeight());
                updateBody.put("status", product.getStatus().getValue());

                if(product.getSaleType() == ProductSaleType.UNLIMITED) {
                    updateBody.put("stock_status", "instock");
                }

                // 1. Опит за намиране на съществуващ превод за езика на сайта
                WpProductTranslationEntity translation = product.getTranslations().stream()
                        .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
                        .findFirst()
                        .orElse(null);

                if (translation == null || translation.getName().isEmpty()) {
                    log.info("Преводът липсва за SKU {} на език {}. Стартиране на ChatGPT превод...",
                            product.getSku(), site.getLanguage().getName());

                    WpProductTranslationEntity base = product.getTranslations().stream()
                            .filter(t -> t.getLanguage().getId() == 1L)
                            .findFirst()
                            .orElse(null);

                    String targetLang = site.getLanguage().getName();
                    String sourceLang = base.getLanguage().getName();

                    String namePrompt = String.format(
                            "Translate this e-commerce product name from %s to %s: '%s'. " +
                                    "IMPORTANT: Return ONLY the translated string. Do not include any quotes, explanations, or introductory text.",
                            sourceLang, targetLang, base.getName()
                    );
                    String translatedName = chatGptService.translateText(base.getName(), namePrompt);

                    String translatedShort = "";
                    if (base.getShortDescription() != null && !base.getShortDescription().isEmpty()) {
                        String shortPrompt = String.format("Translate this product short description from %s to %s. Keep it concise: '%s'",
                                sourceLang, targetLang, base.getShortDescription());
                        translatedShort = chatGptService.translateText(base.getShortDescription(), shortPrompt);
                    }

                    String translatedDesc = "";
                    if (base.getDescription() != null && !base.getDescription().isEmpty()) {
                        String descPrompt = String.format("Translate this product description from %s to %s. IMPORTANT: Preserve all HTML tags and structure exactly as they are.",
                                sourceLang, targetLang);
                        translatedDesc = chatGptService.translateText(base.getDescription(), descPrompt);
                    }

                    if(translation == null) {
                        translation = new WpProductTranslationEntity();
                    }
                    translation.setProduct(product);
                    translation.setLanguage(site.getLanguage());
                    translation.setName(translatedName);
                    translation.setShortDescription(translatedShort);
                    translation.setDescription(translatedDesc);

                    wpProductTranslationRepository.save(translation);
                    product.getTranslations().add(translation);

                    log.info("Успешен превод за SKU {} на език {}", product.getSku(), targetLang);
                }

                updateBody.put("name", translation.getName());
                updateBody.put("short_description", cleanHtml(translation.getShortDescription()));
                updateBody.put("description", cleanHtml(translation.getDescription()));


                // PRICE
                for (WpProductSiteConfigEntity siteConfig : product.getSiteConfigs()) {
                    if(Objects.equals(siteConfig.getSite().getId(), site.getId())) {
//                        updateBody.put("price", siteConfig.getRegularPrice().toString());
                        updateBody.put("regular_price", siteConfig.getRegularPrice().toString());
                        String salePriceStr = (siteConfig.getPrice() != null && siteConfig.getPrice().compareTo(BigDecimal.ONE) >= 0)
                                ? siteConfig.getPrice().toString()
                                : "";

                        updateBody.put("sale_price", salePriceStr);
                    }
                }

                // BRAND
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

                // CATEGORIES
                List<Map<String, Object>> categoriesList = new ArrayList<>();
                if (product.getCategories() != null) {
                    for (WpCategoryEntity category : product.getCategories()) {

                        Optional<WpCategorySiteMappingEntity> mappingOpt =
                                wpCategorySiteMappingRepository
                                        .findByWpCategoryAndSite(category, site);

                        if (mappingOpt.isPresent()) {
                            Long wpCategoryId = mappingOpt.get().getWpId();
                            Map<String, Object> categoryItem = new HashMap<>();
                            categoryItem.put("id", wpCategoryId);
                            categoriesList.add(categoryItem);
                        } else {
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
                            }
                        }
                    }
                }

                updateBody.put("categories", categoriesList);

                // --- ЛОГИКА ЗА СНИМКИТЕ ---
                List<Map<String, Object>> imageList = new ArrayList<>();
                Map<String, Object> finalVideoGalleryMap = new HashMap<>();

                if (product.getImages() != null) {

                    List<WpProductImageEntity> erpVideos = product.getImages().stream()
                            .filter(WpProductImageEntity::isVideo)
                            .toList();

                    // 1. Оптимизация: Превръщаме DTO масива в Map за много бързо търсене на индекси
                    Map<Long, Integer> orderMap = new HashMap<>();
                    if (order != null) {
                        for (int i = 0; i < order.size(); i++) {
                            if (order.get(i).getId() != null) {
                                orderMap.put(order.get(i).getId(), i);
                            }
                        }
                    }

                    // 2. СОРТИРАНЕ: isPrimary ВИНАГИ първо, след това по orderIndex
                    List<WpProductImageEntity> sortedImages = product.getImages().stream()
                            .filter(e -> !e.isVideo())
                            .sorted((a, b) -> {
                                // 1. Абсолютен приоритет за Primary (Винаги най-отпред)
                                boolean aIsPrimary = Boolean.TRUE.equals(a.getIsPrimary());
                                boolean bIsPrimary = Boolean.TRUE.equals(b.getIsPrimary());

                                if (aIsPrimary && !bIsPrimary) return -1;
                                if (!aIsPrimary && bIsPrimary) return 1;

                                // 2. Ако нито една не е Primary (или и двете са), сортираме по масива order
                                if (!orderMap.isEmpty()) {
                                    int indexA = orderMap.getOrDefault(a.getId(), Integer.MAX_VALUE);
                                    int indexB = orderMap.getOrDefault(b.getId(), Integer.MAX_VALUE);

                                    if (indexA != indexB) {
                                        return Integer.compare(indexA, indexB);
                                    }
                                }

                                return 0;
                            })
                            .toList();

                    // 3. ОБРАБОТКА И ЗАПИС НА orderIndex В БАЗАТА
                    int currentOrderIndex = 0;
                    for (WpProductImageEntity imgEntity : sortedImages) {

                        Optional<WpProductImageSiteMappingEntity> mappingOpt = wpProductImageSiteMappingRepository
                                .findByProductImageIdAndSite(imgEntity.getId(), site);

                        WpProductImageSiteMappingEntity mappingToSave = null;

                        if (mappingOpt.isPresent()) {
                            // Снимката вече я има в този сайт
                            mappingToSave = mappingOpt.get();
                            if (mappingToSave.getWpMediaId() != null) {
                                Map<String, Object> imgMap = new HashMap<>();
                                imgMap.put("id", mappingToSave.getWpMediaId());
                                imageList.add(imgMap);
                            }
                        } else {
                            boolean isBrandNewImage = imgEntity.getSiteMappings() == null || imgEntity.getSiteMappings().isEmpty();

                            if (isBrandNewImage) {
                                log.info("Качване на НОВА снимка към сайт {}: {}", site.getUrl(), imgEntity.getLocalSrc());
                                Long wpMediaId = imageToWordPress.uploadImageToWordPress(site, imgEntity.getLocalSrc());

                                if (wpMediaId != null) {
                                    mappingToSave = new WpProductImageSiteMappingEntity();
                                    mappingToSave.setWpMediaId(wpMediaId);
                                    mappingToSave.setSite(site);
                                    mappingToSave.setProductImage(imgEntity);

                                    Map<String, Object> imgMap = new HashMap<>();
                                    imgMap.put("id", wpMediaId);
                                    imageList.add(imgMap);
                                }
                            } else {
                                log.info("Снимка {} е локална за друг сайт. Пропускам качване в {}.",
                                        imgEntity.getId(), site.getUrl());
                            }
                        }

                        // ОБНОВЯВАМЕ orderIndex В МАСИВА
                        if (mappingToSave != null) {
                            mappingToSave.setOrderIndex(currentOrderIndex);
                            wpProductImageSiteMappingRepository.save(mappingToSave);
                        }

                        currentOrderIndex++;
                    }

                    // ВИДЕА (остават непроменени)
                    for (WpProductImageEntity video : erpVideos) {
                        if (video.getParent() == null) continue;

                        Optional<WpProductImageSiteMappingEntity> videoMappingOpt = wpProductImageSiteMappingRepository
                                .findByProductImageIdAndSite(video.getId(), site);

                        Long wpVideoId = null;
                        String wpVideoUrl = null;

                        if (videoMappingOpt.isPresent()) {
                            wpVideoId = videoMappingOpt.get().getWpMediaId();
                            wpVideoUrl = videoMappingOpt.get().getWpUrl();
                        } else {
                            boolean isBrandNewVideo = video.getSiteMappings() == null || video.getSiteMappings().isEmpty();

                            if (isBrandNewVideo) {
                                log.info("🎬 Качване на НОВО видео към сайт {}: {}", site.getUrl(), video.getLocalSrc());
                                Map<String, Object> uploadResult = imageToWordPress.uploadVideoToWordPress(site, video.getLocalSrc());

                                if (uploadResult != null && uploadResult.containsKey("id")) {
                                    wpVideoId = Long.valueOf(uploadResult.get("id").toString());
                                    wpVideoUrl = uploadResult.get("url").toString();

                                    WpProductImageSiteMappingEntity newVideoMapping = new WpProductImageSiteMappingEntity();
                                    newVideoMapping.setWpMediaId(wpVideoId);
                                    newVideoMapping.setSite(site);
                                    newVideoMapping.setProductImage(video);
                                    newVideoMapping.setWpUrl(wpVideoUrl);
                                    wpProductImageSiteMappingRepository.save(newVideoMapping);
                                }
                            } else {
                                log.info("Видео {} е локално за друг сайт. Пропускам качване в {}.", video.getId(), site.getUrl());
                            }
                        }

                        if (wpVideoId != null) {
                            Optional<WpProductImageSiteMappingEntity> imgMappingOpt = wpProductImageSiteMappingRepository
                                    .findByProductImageIdAndSite(video.getParent().getId(), site);

                            if (imgMappingOpt.isPresent()) {
                                String parentWpMediaIdStr = imgMappingOpt.get().getWpMediaId().toString();

                                if (wpVideoUrl != null || !finalVideoGalleryMap.containsKey(parentWpMediaIdStr)) {
                                    Map<String, Object> videoDetails = new HashMap<>();
                                    videoDetails.put("video_type", "mp4");
                                    videoDetails.put("upload_video_id", wpVideoId.toString());
                                    if (wpVideoUrl != null) {
                                        videoDetails.put("upload_video_url", wpVideoUrl);
                                    }
                                    videoDetails.put("autoplay", "0");
                                    videoDetails.put("video_size", "contain");
                                    videoDetails.put("video_control", "theme");
                                    videoDetails.put("hide_gallery_img", "0");
                                    videoDetails.put("hide_information", "0");
                                    videoDetails.put("audio_status", "unmute");

                                    finalVideoGalleryMap.put(parentWpMediaIdStr, videoDetails);
                                }
                            }
                        }
                    }
                }

                updateBody.put("images", imageList);

                currentMeta.removeIf(meta -> "woodmart_wc_video_gallery".equals(meta.get("key")));
                Map<String, Object> videoMetaEntry = new HashMap<>();
                videoMetaEntry.put("key", "woodmart_wc_video_gallery");
                videoMetaEntry.put("value", finalVideoGalleryMap);
                currentMeta.add(videoMetaEntry);
                updateBody.put("meta_data", currentMeta);


                CurrencyEntity currency = site.getCurrency();

                // 7. ADDONS
                List<Map<String, Object>> wooAddons = generateWooAddons(product, site, currency);
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
                        Set<Long> idsToDeleteFromMediaLibrary = wpImagesFromSearch.stream()
                                .map(img -> Long.valueOf(img.get("id").toString()))
                                .filter(id -> !erpWpMediaIds.contains(id))
                                .collect(Collectors.toSet());

                        if (!idsToDeleteFromMediaLibrary.isEmpty()) {
                            imageToWordPress.deleteMediaOneByOne(site, idsToDeleteFromMediaLibrary, auth);
                            log.info("Изтрити са {} излишни медийни файла от сайт {}", idsToDeleteFromMediaLibrary.size(), site.getUrl());
                        }
                    }
                }

            } catch (Exception e) {
                log.error("Грешка при обновяване на сайт {}: {}", site.getUrl(), e.getMessage());
            }
        }
    }

//    @Transactional
//    @Async
//    public void updateProductOnSites(WpProductEntity product, Long lastEditedSiteId, List<WpProductImageDto> order) throws InterruptedException {
//        Thread.sleep(2000);
//        product = wpProductRepository.findById(product.getId()).orElse(null);
//        if(product == null) return;
//
//
//        // --- КРИТИЧНА ЧАСТ: FORCE INITIALIZATION ---
//        // Форсираме зареждането на адоните и ВСИЧКИ техни преводи за ВСИЧКИ езици
//        if (product.getAddonConfig() != null) {
//            product.getAddonConfig().forEach(config -> {
//                // Инициализираме стойността на адона
//                WpAddonValueEntity val = config.getAddonValue();
//                val.getTranslations().size(); // Зарежда преводите на стойността (напр. "Червен")
//
//                // Инициализираме групата и нейните преводи (напр. "Цвят")
//                if (val.getGroups() != null && !val.getGroups().isEmpty()) {
//                    val.getGroups().forEach(group -> group.getTranslations().size());
//                }
//            });
//        }
//        // --- КРАЙ НА ИНИЦИАЛИЗАЦИЯТА ---
//
//
////        List<SiteEntity> siteList = siteRepository.findAll();
//        List<SiteEntity> siteList;
//
//        // Ако lastEditedSiteId е null (т.е. нов продукт или глобална промяна), вземаме всички сайтове
//        if (lastEditedSiteId == null) {
//            siteList = siteRepository.findAll();
////            log.info("Стартиране на глобална синхронизация за SKU: {}", product.getSku());
//        }
//        else {
//            // Ако е подаден конкретен сайт, обновяваме само него
//            siteList = siteRepository.findById(lastEditedSiteId)
//                    .map(List::of) // Превръщаме в списък с един елемент
//                    .orElse(Collections.emptyList());
////            log.info("Стартиране на локална синхронизация за SKU: {} само за сайт ID: {}", product.getSku(), lastEditedSiteId);
//        }
//
//        for (SiteEntity site : siteList) {
//            try {
//                String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
//
//                var searchResponse = restClient.get()
//                        .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products?sku=" + product.getSku())
//                        .header("Authorization", "Basic " + auth)
//                        .retrieve()
//                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
//
//                boolean isNewProduct = false;
//                if (searchResponse == null || searchResponse.isEmpty()) {
//                    log.warn("Продукт с SKU {} не е намерен в сайта {}", product.getSku(), site.getUrl());
//                    isNewProduct = true;
//                }
//                List<Map<String, Object>> currentMeta = new ArrayList<>();
//                if(!isNewProduct) {
//                    currentMeta = (List<Map<String, Object>>) searchResponse.get(0).get("meta_data");
//                }
//
////                List<Map<String, Object>> currentMeta = (List<Map<String, Object>>) searchResponse.get(0).get("meta_data");
////                if (currentMeta == null) {
////                    currentMeta = new ArrayList<>();
////                }
//
//                //                        String newLabelValue = !salePriceStr.isEmpty() ? "on" : "";
////                        boolean keyExists = false;
////                        for (Map<String, Object> meta : currentMeta) {
////                            if ("_woodmart_new_label".equals(meta.get("key"))) {
////                                meta.put("value", newLabelValue);
////                                keyExists = true;
////                                break;
////                            }
////                        }
////                        if (!keyExists) {
////                            Map<String, Object> newMeta = new HashMap<>();
////                            newMeta.put("key", "_woodmart_new_label");
////                            newMeta.put("value", newLabelValue);
////                            currentMeta.add(newMeta);
////                        }
////                        updateBody.put("meta_data", currentMeta);
//
//
//                Map<String, Object> updateBody = new HashMap<>();
//                updateBody.put("stock_quantity", product.getStockQuantity());
//                updateBody.put("manage_stock", product.getSaleType() != ProductSaleType.UNLIMITED);
//                updateBody.put("backorders", product.getSaleType() == ProductSaleType.UNLIMITED? "yes": "no");
//                updateBody.put("backorders_allowed", product.getSaleType() == ProductSaleType.UNLIMITED);
//                updateBody.put("weight", product.getWeight());
//                updateBody.put("status", product.getStatus().getValue());
//
//                if(product.getSaleType() == ProductSaleType.UNLIMITED) {
//                    updateBody.put("stock_status", "instock");
//                }
//
////                DESCRIPTION
////                for (WpProductTranslationEntity translation : product.getTranslations()) {
////                    if(translation.getLanguage() == site.getLanguage()) {
////                        updateBody.put("short_description", translation.getShortDescription());
////                        updateBody.put("description", translation.getDescription());
////                        updateBody.put("name", translation.getName());
////                        break;
////                    }
////                }
//
//                // 1. Опит за намиране на съществуващ превод за езика на сайта
//                WpProductTranslationEntity translation = product.getTranslations().stream()
//                        .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
//                        .findFirst()
//                        .orElse(null);
//
//                if (translation == null || translation.getName().isEmpty()) {
//                    log.info("Преводът липсва за SKU {} на език {}. Стартиране на ChatGPT превод...",
//                            product.getSku(), site.getLanguage().getName());
//
//                    // Вземаме изходния текст (обикновено първия наличен превод, напр. Български)
//                    // НАМИРАМЕ БЪЛГАРСКИЯ ПРЕВОД (id: 1) КАТО ИЗТОЧНИК
//                    WpProductTranslationEntity base = product.getTranslations().stream()
//                            .filter(t -> t.getLanguage().getId() == 1L) // Тук приемаме, че BG ID е 1
//                            .findFirst()
//                            .orElse(null);
//
//                    String targetLang = site.getLanguage().getName();
//                    String sourceLang = base.getLanguage().getName();
//
//                    // Превод на Името
//                    String namePrompt = String.format(
//                            "Translate this e-commerce product name from %s to %s: '%s'. " +
//                                    "IMPORTANT: Return ONLY the translated string. Do not include any quotes, explanations, or introductory text.",
//                            sourceLang, targetLang, base.getName()
//                    );
//                    String translatedName = chatGptService.translateText(base.getName(), namePrompt);
//
//                    // Превод на Краткото описание
//                    String translatedShort = "";
//                    if (base.getShortDescription() != null && !base.getShortDescription().isEmpty()) {
//                        String shortPrompt = String.format("Translate this product short description from %s to %s. Keep it concise: '%s'",
//                                sourceLang, targetLang, base.getShortDescription());
//                        translatedShort = chatGptService.translateText(base.getShortDescription(), shortPrompt);
//                    }
//
//                    // Превод на Дългото описание (с внимание към HTML)
//                    String translatedDesc = "";
//                    if (base.getDescription() != null && !base.getDescription().isEmpty()) {
//                        String descPrompt = String.format("Translate this product description from %s to %s. IMPORTANT: Preserve all HTML tags and structure exactly as they are.",
//                                sourceLang, targetLang);
//                        translatedDesc = chatGptService.translateText(base.getDescription(), descPrompt);
//                    }
//
//                    // 2. Запис в базата данни, за да не се превежда отново при следващ синк
//                    if(translation == null) {
//                        translation = new WpProductTranslationEntity();
//                    }
//                    translation.setProduct(product);
//                    translation.setLanguage(site.getLanguage());
//                    translation.setName(translatedName);
//                    translation.setShortDescription(translatedShort);
//                    translation.setDescription(translatedDesc);
//
//                    wpProductTranslationRepository.save(translation);
//
//                    // Добавяме го в списъка на обекта в паметта, за да го ползваме веднага
//                    product.getTranslations().add(translation);
//
//                    log.info("Успешен превод за SKU {} на език {}", product.getSku(), targetLang);
//                }
//
//// 3. Попълваме тялото на заявката към WooCommerce
//                updateBody.put("name", translation.getName());
//                updateBody.put("short_description", cleanHtml(translation.getShortDescription()));
//                updateBody.put("description", cleanHtml(translation.getDescription()));
//
//
//
////                PRICE
//                for (WpProductSiteConfigEntity siteConfig : product.getSiteConfigs()) {
//                    if(Objects.equals(siteConfig.getSite().getId(), site.getId())) {
//                        updateBody.put("price", siteConfig.getRegularPrice().toString());
//                        updateBody.put("regular_price", siteConfig.getRegularPrice().toString());
//                        String salePriceStr = (siteConfig.getPrice() != null && siteConfig.getPrice().compareTo(BigDecimal.ONE) >= 0)
//                                ? siteConfig.getPrice().toString()
//                                : "";
//
//                        updateBody.put("sale_price", salePriceStr);
//                    }
//                }
////                BRAND
//                if(product.getBrand() != null) {
//                    var searchResponseBrand = restClient.get()
//                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/brands?slug=" + product.getBrand().getSlug())
//                            .header("Authorization", "Basic " + auth)
//                            .retrieve()
//                            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
//
//                    Long wpBrandId = null;
//                    if (searchResponseBrand != null && !searchResponseBrand.isEmpty()) {
//                        wpBrandId = Long.valueOf(searchResponseBrand.get(0).get("id").toString());
//                    }
//
//                    if(wpBrandId != null) {
//
//                        Map<String, Object> brandObject = new HashMap<>();
//                        brandObject.put("id", wpBrandId);
//
//                        updateBody.put("brands", List.of(brandObject));
//                    }
//                }
//
////                CATEGORIES
//                List<Map<String, Object>> categoriesList = new ArrayList<>();
//                if (product.getCategories() != null) {
//                    for (WpCategoryEntity category : product.getCategories()) {
//
//                        // 1. Търсим мапинга в нашата база данни за този конкретен сайт
//                        // Използваме репозиторито, което вече трябва да имаш инжектирано
//                        Optional<WpCategorySiteMappingEntity> mappingOpt =
//                                wpCategorySiteMappingRepository
//                                .findByWpCategoryAndSite(category, site);
//
//                        if (mappingOpt.isPresent()) {
//                            // Ако имаме запис, вземаме директно wpId
//                            Long wpCategoryId = mappingOpt.get().getWpId();
//
//                            Map<String, Object> categoryItem = new HashMap<>();
//                            categoryItem.put("id", wpCategoryId);
//                            categoriesList.add(categoryItem);
//                        } else {
//                            // 2. ФАЛБЕК: Ако по някаква причина нямаме мапинг, търсим по slug в API-то
//                            // (Това е твоята стара логика, но като резервен вариант)
//                            log.warn("Няма мапинг за категория {} в сайт {}. Търсене чрез API...", category.getSlug(), site.getUrl());
//
//                            var searchResponseCategory = restClient.get()
//                                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories?slug=" + category.getSlug())
//                                    .header("Authorization", "Basic " + auth)
//                                    .retrieve()
//                                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
//
//                            if (searchResponseCategory != null && !searchResponseCategory.isEmpty()) {
//                                Long wpCategoryId = Long.valueOf(searchResponseCategory.get(0).get("id").toString());
//                                Map<String, Object> categoryItem = new HashMap<>();
//                                categoryItem.put("id", wpCategoryId);
//                                categoriesList.add(categoryItem);
//
//                                // Опционално: Създай мапинг тук, за да не търсиш следващия път
//                            }
//                        }
//                    }
//                }
//
//                // Винаги подаваме списъка (дори и празен, ако искаме да изчистим категориите в WP)
//                updateBody.put("categories", categoriesList);
//
//
//                List<Map<String, Object>> imageList = new ArrayList<>();
//                Map<String, Object> finalVideoGalleryMap = new HashMap<>();
//
//                if (product.getImages() != null) {
//
//                    List<WpProductImageEntity> erpVideos = product.getImages().stream()
//                            .filter(WpProductImageEntity::isVideo)
//                            .toList();
//
//                    // СОРТИРАНЕ: Слагаме Primary снимката най-отпред
//                    List<WpProductImageEntity> sortedImages = product.getImages().stream()
//                            .filter(e -> !e.isVideo())
//                            .sorted((a, b) -> Boolean.compare(b.getIsPrimary(), a.getIsPrimary()))
//                            .toList();
//
//                    for (WpProductImageEntity imgEntity : sortedImages) {
//
//                        // 1. Търсим съществуващ мапинг за текущия сайт
//                        Optional<WpProductImageSiteMappingEntity> mappingOpt = wpProductImageSiteMappingRepository
//                                .findByProductImageIdAndSite(imgEntity.getId(), site);
//
//                        if (mappingOpt.isPresent()) {
//                            // Снимката вече съществува в този сайт - добавяме я в списъка на продукта
//                            Map<String, Object> imgMap = new HashMap<>();
//                            imgMap.put("id", mappingOpt.get().getWpMediaId());
//                            imageList.add(imgMap);
//                        } else {
//                            // 2. КЛЮЧОВАТА ПРОВЕРКА: Качваме снимката САМО ако тя е "нова" за системата
//                            // Проверяваме дали снимката има мапинги изобщо.
//                            // Ако НЯМА никакви мапинги, значи е току-що качена от Angular и чака в temp.
//
//                            boolean isBrandNewImage = imgEntity.getSiteMappings() == null || imgEntity.getSiteMappings().isEmpty();
//
//                            if (isBrandNewImage) {
//                                log.info("Качване на НОВА снимка към сайт {}: {}", site.getUrl(), imgEntity.getLocalSrc());
//                                Long wpMediaId = imageToWordPress.uploadImageToWordPress(site, imgEntity.getLocalSrc());
//
//                                if (wpMediaId != null) {
//                                    WpProductImageSiteMappingEntity newMapping = new WpProductImageSiteMappingEntity();
//                                    newMapping.setWpMediaId(wpMediaId);
//                                    newMapping.setSite(site);
//                                    newMapping.setProductImage(imgEntity);
//                                    wpProductImageSiteMappingRepository.save(newMapping);
//
//                                    Map<String, Object> imgMap = new HashMap<>();
//                                    imgMap.put("id", wpMediaId);
//                                    imageList.add(imgMap);
//                                }
//                            } else {
//                                // Снимката има мапинги за други сайтове, но не и за този.
//                                // Тъй като не е "нова" (temp), ние НЕ я качваме тук автоматично.
//                                log.info("Снимка {} е локална за друг сайт. Пропускам качване в {}.",
//                                        imgEntity.getId(), site.getUrl());
//                            }
//                        }
//                    }
//
//                    for (WpProductImageEntity video : erpVideos) {
//                        if (video.getParent() == null) continue; // Застраховка, че видеото има снимка-родител
//
//                        Optional<WpProductImageSiteMappingEntity> videoMappingOpt = wpProductImageSiteMappingRepository
//                                .findByProductImageIdAndSite(video.getId(), site);
//
//                        Long wpVideoId = null;
//                        String wpVideoUrl = null;
//
//                        if (videoMappingOpt.isPresent()) {
//                            // Видеото вече съществува в този сайт
//                            wpVideoId = videoMappingOpt.get().getWpMediaId();
//                            wpVideoUrl = videoMappingOpt.get().getWpUrl();
//                            // Забележка: Тъй като WoodMart изисква и URL, ако уебсайтът не ни го връща в базата,
//                            // е най-добре да разчитаме, че ако finalVideoGalleryMap вече съдържа мета данните от уебсайта, няма да ги презаписваме.
//                        } else {
//                            // Качваме видеото САМО ако е чисто ново за системата (точно както при снимките)
//                            boolean isBrandNewVideo = video.getSiteMappings() == null || video.getSiteMappings().isEmpty();
//
//                            if (isBrandNewVideo) {
//                                log.info("🎬 Качване на НОВО видео към сайт {}: {}", site.getUrl(), video.getLocalSrc());
//                                Map<String, Object> uploadResult = imageToWordPress.uploadVideoToWordPress(site, video.getLocalSrc());
//
//                                if (uploadResult != null && uploadResult.containsKey("id")) {
//                                    wpVideoId = Long.valueOf(uploadResult.get("id").toString());
//                                    wpVideoUrl = uploadResult.get("url").toString();
//
//                                    // Записваме новия мапинг за видеото за този сайт
//                                    WpProductImageSiteMappingEntity newVideoMapping = new WpProductImageSiteMappingEntity();
//                                    newVideoMapping.setWpMediaId(wpVideoId);
//                                    newVideoMapping.setSite(site);
//                                    newVideoMapping.setProductImage(video);
//                                    newVideoMapping.setWpUrl(wpVideoUrl);
//                                    newVideoMapping.setWpMediaId(wpVideoId);
//                                    wpProductImageSiteMappingRepository.save(newVideoMapping);
//                                }
//                            } else {
//                                log.info("Видео {} е локално за друг сайт. Пропускам качване в {}.", video.getId(), site.getUrl());
//                            }
//                        }
//
//                        // СЛЕД ПРОВЕРКАТА: Ако имаме успешно намерено или качено видео, го обвързваме с неговата снимка
//                        if (wpVideoId != null) {
//                            // Търсим WordPress ID-то на снимката-родител за ТОЗИ сайт
//                            Optional<WpProductImageSiteMappingEntity> imgMappingOpt = wpProductImageSiteMappingRepository
//                                    .findByProductImageIdAndSite(video.getParent().getId(), site);
//
//                            if (imgMappingOpt.isPresent()) {
//                                String parentWpMediaIdStr = imgMappingOpt.get().getWpMediaId().toString();
//
//                                // Ако видеото току-що е качено (имаме URL), или ако го няма изобщо в WoodMart мапа - го добавяме/обновяваме
//                                if (wpVideoUrl != null || !finalVideoGalleryMap.containsKey(parentWpMediaIdStr)) {
//                                    Map<String, Object> videoDetails = new HashMap<>();
//                                    videoDetails.put("video_type", "mp4");
//                                    videoDetails.put("upload_video_id", wpVideoId.toString());
//                                    if (wpVideoUrl != null) {
//                                        videoDetails.put("upload_video_url", wpVideoUrl);
//                                    }
//                                    videoDetails.put("autoplay", "0");
//                                    videoDetails.put("video_size", "contain");
//                                    videoDetails.put("video_control", "theme");
//                                    videoDetails.put("hide_gallery_img", "0");
//                                    videoDetails.put("hide_information", "0");
//                                    videoDetails.put("audio_status", "unmute");
//
//                                    finalVideoGalleryMap.put(parentWpMediaIdStr, videoDetails);
//                                }
//                            }
//                        }
//                    }
//                }
//                    updateBody.put("images", imageList);
//
//                currentMeta.removeIf(meta -> "woodmart_wc_video_gallery".equals(meta.get("key")));
//                    Map<String, Object> videoMetaEntry = new HashMap<>();
//                    videoMetaEntry.put("key", "woodmart_wc_video_gallery");
//                    videoMetaEntry.put("value", finalVideoGalleryMap);
//                    currentMeta.add(videoMetaEntry);
//                    updateBody.put("meta_data", currentMeta);
//
//
//                CurrencyEntity currency = site.getCurrency();
//
//                // 7. ADDONS (FORCE GENERATION)
//                List<Map<String, Object>> wooAddons = generateWooAddons(product, site, currency);
//                updateBody.put("addons", wooAddons);
//
//
//
//
//                if(isNewProduct) {
//                    updateBody.put("sku", product.getSku());
//
//                    restClient.post()
//                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products")
//                            .header("Authorization", "Basic " + auth)
//                            .body(updateBody)
//                            .retrieve()
//                            .toBodilessEntity();
//                    log.info("Успешно създаден нов продукт с SKU {} в сайт {}", product.getSku(), site.getUrl());
//                }
//                else {
//                    Integer wpId = (Integer) searchResponse.get(0).get("id");
//                                        restClient.patch()
//                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/" + wpId)
//                            .header("Authorization", "Basic " + auth)
//                            .body(updateBody)
//                            .retrieve()
//                            .toBodilessEntity();
//
//
//                    Set<Long> erpWpMediaIds = imageList.stream()
//                            .map(img -> Long.valueOf(img.get("id").toString()))
//                            .collect(Collectors.toSet());
//
//                    List<Map<String, Object>> wpImagesFromSearch = (List<Map<String, Object>>) searchResponse.get(0).get("images");
//
//                    if (wpImagesFromSearch != null) {
//                        // Намираме кои ID-та съществуват в WP, но ги няма в нашия нов списък от ERP
//                        Set<Long> idsToDeleteFromMediaLibrary = wpImagesFromSearch.stream()
//                                .map(img -> Long.valueOf(img.get("id").toString()))
//                                .filter(id -> !erpWpMediaIds.contains(id)) // Ако го няма в ERP списъка -> за триене
//                                .collect(Collectors.toSet());
//
//                        // 3. Физическо триене от Media Library
//                        if (!idsToDeleteFromMediaLibrary.isEmpty()) {
//                            imageToWordPress.deleteMediaOneByOne(site, idsToDeleteFromMediaLibrary, auth);
//                            log.info("Изтрити са {} излишни медийни файла от сайт {}", idsToDeleteFromMediaLibrary.size(), site.getUrl());
//                        }
//                    }
//                }
//
//            } catch (Exception e) {
//                log.error("Грешка при обновяване на сайт {}: {}", site.getUrl(), e.getMessage());
//            }
//
//
//
//        }
//
//
//    }

    private List<Map<String, Object>> generateWooAddons(WpProductEntity product, SiteEntity site, CurrencyEntity currency) {
        List<Map<String, Object>> wooAddons = new ArrayList<>();
        if (product.getAddonConfig() == null || product.getAddonConfig().isEmpty()) return wooAddons;

        if (product.getAddonConfig() != null && !product.getAddonConfig().isEmpty()) {

            // 1. Сортираме адоните по ID (ред на добавяне), за да запазим подредбата от БД
            List<WpProductAddonConfigEntity> sortedConfigs = product.getAddonConfig().stream()
                    .sorted(Comparator.comparing(BaseEntity::getId))
                    .toList();

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
                addonGroupMap.put("default", "0");

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
                    String priceRs = "";
//                    System.out.println(currency.getCode().toUpperCase());
                    if(config.getPriceModifier().compareTo(BigDecimal.ZERO) > 0) {
                        if(!currency.getCode().equals("EUR")) {
                            BigDecimal convert = currencyService.convert(config.getPriceModifier(), "EUR", currency.getCode().toUpperCase());
                            priceRs = convert.toString();
                        } else {
                            priceRs = config.getPriceModifier().toString();
                        }
                    }
                    option.put("price", priceRs);
//                    option.put("price", config.getPriceModifier().compareTo(BigDecimal.ZERO) > 0
//                            ? config.getPriceModifier().toString() : "");
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
        return wooAddons;
    }

    private String cleanHtml(String html) {
        if (html == null) return "";

        // 1. Първо оправяме "твърдите" интервали
        String result = html.replaceAll("&nbsp;", " ");

        // 2. АКО ТЕКСТЪТ ИДВА ОТ TEXTAREA (без тагове), той ползва \n
        // Превръщаме двойните нови редове в параграф с невидимо съдържание
        result = result.replaceAll("\n\n", "</p><p>&nbsp;</p><p>");

        // 3. Единичните нови редове превръщаме в затваряне и отваряне на параграф
        result = result.replaceAll("\n", "</p><p>");

        // 4. Обвиваме целия текст в начален и краен параграф
        result = "<p>" + result + "</p>";

        // 5. ПОЧИСТВАНЕ: Премахваме празни параграфи, създадени от грешни преноси,
        // но ГАРАНТИРАМЕ, че тези с &nbsp; остават
        result = result.replaceAll("<p>\\s*</p>", "");

        // Премахваме излишни стилове, ако случайно са влезли
        result = result.replaceAll(" style=\"[^\"]*\"", "");

        return result.trim();
    }




    @Transactional
    @Async
    public void updateProductOnSitesOnlyPrices(WpProductEntity product, Long lastEditedSiteId) throws InterruptedException {
        Thread.sleep(2000);
        log.info("започва за продукт {}", product.getSku());
        product = wpProductRepository.findById(product.getId()).orElse(null);
        if(product == null) return;


        List<SiteEntity> siteList = siteRepository.findById(lastEditedSiteId)
                .map(List::of) // Превръщаме в списък с един елемент
                .orElse(Collections.emptyList());


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

                if (searchResponse == null || searchResponse.isEmpty()) {
                    log.warn("Продукт с SKU {} не е намерен в сайта {}", product.getSku(), site.getUrl());
                   return;
                }

                Map<String, Object> updateBody = new HashMap<>();

                List<Map<String, Object>> currentAddons = (List<Map<String, Object>>) searchResponse.get(0).get("addons");

                if (currentAddons != null && !currentAddons.isEmpty()) {

                    // Преминаваме през всяка група адони (напр. "Размер")
                    for (Map<String, Object> addonGroup : currentAddons) {
                        List<Map<String, Object>> options = (List<Map<String, Object>>) addonGroup.get("options");

                        if (options != null) {
                            for (Map<String, Object> option : options) {
                                String rawPrice = (String) option.get("price");

                                if (rawPrice != null && !rawPrice.isEmpty()) {
                                    // ПРЕВАЛУТИРАМЕ: Приемаме, че в сайта цената е била в EUR
                                    BigDecimal eurPrice = new BigDecimal("5.10");
                                    BigDecimal ronPrice = currencyService.convert(eurPrice, "EUR", "RON");

                                    // Записваме новата цена в обекта
                                    option.put("price", ronPrice.toString());
                                }
                            }
                        }
                    }

                    // Добавяме превалутираните адони към тялото за обновяване
                    updateBody.put("addons", currentAddons);
                }

                Integer wpId = (Integer) searchResponse.get(0).get("id");
                    restClient.patch()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/" + wpId)
                            .header("Authorization", "Basic " + auth)
                            .body(updateBody)
                            .retrieve()
                            .toBodilessEntity();
                log.info("успешно за продукт {}", product.getSku());

            } catch (Exception e) {
                log.error("Грешка при обновяване на сайт {}: {}", site.getUrl(), e.getMessage());
            }

        }


    }

    @Transactional
    @Async
    public void updateProductNameAnInfo(WpProductEntity product, Long siteId) {
        log.info("започва за продукт {}", product.getSku());
        product = wpProductRepository.findById(product.getId()).orElse(null);
        if(product == null) return;


        List<SiteEntity> siteList = siteRepository.findById(siteId)
                .map(List::of) // Превръщаме в списък с един елемент
                .orElse(Collections.emptyList());

        for (SiteEntity site : siteList) {
//            if(site.getId().equals(sourceSiteId) || site.getUrl().equals("sateno.bg")) continue;
//            System.out.println(site.toString());
            String description = null;
            String nameT = "";
            String descriptionT = "";
            try {
                Thread.sleep(2000);
                String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

                var searchResponse = restClient.get()
                        .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products?sku=" + product.getSku())
                        .header("Authorization", "Basic " + auth)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                        });

                if (searchResponse == null || searchResponse.isEmpty()) {
                    log.warn("Продукт с SKU {} не е намерен в сайта {}", product.getSku(), site.getUrl());
                    return;
                }

                Map<String, Object> updateBody = new HashMap<>();

//                Object priceObj = searchResponse.get(0).get("price");
//                String price = priceObj != null ? priceObj.toString() : "";
//                String salePrice = searchResponse.get(0).get("sale_price").toString();
//                if(price.isEmpty() || price.equals("0") || price.equals("0.0") || price.equals("0.00")) {
//                log.info("obnovqva se !!!!!!!!!! {} {}", product.getSku(), price);

//                Optional<WpProductSiteConfigEntity> g = wpProductSiteConfigRepository.findByProductAndSite(product, site);
//                    if (g.isPresent()) {
//                        WpProductSiteConfigEntity gg = g.get();
//
//                        BigDecimal regularPrice = gg.getRegularPrice();
//                        BigDecimal salePrice = gg.getPrice();
//
//                        // Ако няма цена - вземи от site 6 (EUR)
//                        if (regularPrice == null || regularPrice.compareTo(BigDecimal.ZERO) == 0 || salePrice == null || salePrice.compareTo(BigDecimal.ZERO) == 0 || regularPrice.compareTo(salePrice) == 0) {
//                            SiteEntity bgSite = siteRepository.findById(6L).orElse(null);
//                            if (bgSite != null) {
//                                Optional<WpProductSiteConfigEntity> bgConfig = wpProductSiteConfigRepository.findByProductAndSite(product, bgSite);
//                                if (bgConfig.isPresent()) {
//                                    BigDecimal eurPrice = bgConfig.get().getRegularPrice();
//                                    BigDecimal eurPrice2 = bgConfig.get().getPrice();
//                                    if (eurPrice != null && eurPrice.compareTo(BigDecimal.ZERO) > 0) {
//                                        regularPrice = currencyService.convert(eurPrice, bgSite.getCurrency().getCode(), site.getCurrency().getCode());
////                                        salePrice = currencyService.convert(eurPrice2, "EUR", site.getCurrency().getCode());
//                                        salePrice = eurPrice2 != null ? currencyService.convert(eurPrice2, bgSite.getCurrency().getCode(), site.getCurrency().getCode()) : null; // ← добави проверка
//
//                                    }
//                                }
//                            }
//                        }
//
//                        updateBody.put("sale_price", salePrice != null && salePrice.compareTo(BigDecimal.ZERO) > 0 ? salePrice.toString() : "");
////                        updateBody.put("price", regularPrice != null ? regularPrice.toString() : "");
//                        updateBody.put("regular_price", regularPrice != null ? regularPrice.toString() : "");
//                        log.info(updateBody.toString());
//                        gg.setRegularPrice(regularPrice);
//                        gg.setSalePrice(salePrice != null && salePrice.compareTo(BigDecimal.ZERO) > 0 ? salePrice: null);
//                        gg.setPrice(salePrice);
//                        wpProductSiteConfigRepository.save(gg);
//                    }
//                }



//                SiteEntity bgSite = siteRepository.findById(6L).orElse(null);
//                if (bgSite != null) {
//                    Optional<WpProductTranslationEntity> byProductAndLanguage = wpProductTranslationRepository.findByProductAndLanguage(product, bgSite.getLanguage());
//                    if (byProductAndLanguage.isPresent()) {
//                        String prompt = String.format("Да се преведе текста от %s на %s език", bgSite.getLanguage().getCode(), site.getLanguage().getCode());
//                        nameT = chatGptService.translateText(byProductAndLanguage.get().getName(), prompt);
////                        String shortDescription = chatGptService.translateText(byProductAndLanguage.get().getShortDescription(), prompt);
//                        descriptionT = chatGptService.translateText(byProductAndLanguage.get().getDescription(), prompt);
//
//                        Optional<WpProductTranslationEntity> byProductAndLanguage2 = wpProductTranslationRepository.findByProductAndLanguage(product, site.getLanguage());
//                        String finalNameT = nameT;
//                        String finalDescriptionT = descriptionT;
//                        byProductAndLanguage2.ifPresent(wpProductTranslationEntity -> {
//                            if (finalNameT != null && !finalNameT.isEmpty()) {
//                                updateBody.put("name", finalNameT);
//                                wpProductTranslationEntity.setName(finalNameT);
//                            }
//                            if (finalDescriptionT != null && !finalDescriptionT.isEmpty()) {
//                                updateBody.put("description", finalDescriptionT);
//                                wpProductTranslationEntity.setDescription(finalDescriptionT);
//                            }
////                        if(shortDescription != null && !shortDescription.isEmpty()) {
////                            updateBody.put("short_description", shortDescription != null? shortDescription: "");
////                            wpProductTranslationEntity.setShortDescription(shortDescription != null? shortDescription: "");
////                        }
//                            wpProductTranslationRepository.save(wpProductTranslationEntity);
//                        });
//                    }
//
//                }

//                Optional<WpProductTranslationEntity> byProductAndLanguage = wpProductTranslationRepository.findByProductAndLanguage(product, site.getLanguage());
//                byProductAndLanguage.ifPresent(wpProductTranslationEntity -> {
//                    updateBody.put("name", wpProductTranslationEntity.getName());
//                    updateBody.put("description", wpProductTranslationEntity.getDescription());
//                    updateBody.put("short_description", wpProductTranslationEntity.getShortDescription());
//                });

//                WpProductSiteConfigEntity baseConfig = product.getSiteConfigs().stream()
//                        .filter(c -> c.getSite().equals(site))
//                        .findFirst()
//                        .orElse(null);
//                if(baseConfig != null) {
//                    updateBody.put("sale_price", baseConfig.getSalePrice() != null ? baseConfig.getSalePrice().toString() : "");
//                    updateBody.put("price", baseConfig.getPrice() != null ? baseConfig.getPrice().toString() : "");
//                    updateBody.put("regular_price", baseConfig.getPrice() != null ? baseConfig.getPrice().toString() : "");
//                }


                List<Map<String, Object>> currentAddons = (List<Map<String, Object>>) searchResponse.get(0).get("addons");
                if (currentAddons != null && !currentAddons.isEmpty()) {

                    // Преминаваме през всяка група адони (напр. "Размер")
                    for (Map<String, Object> addonGroup : currentAddons) {


//                        String addonName = (String) addonGroup.get("name");
//                        if (addonName != null) {
//                            String translatedAddonName = chatGptService.translateText(addonName, String.format("Да се преведе текста на %s език", site.getLanguage().getCode()));
//                            addonGroup.put("name", translatedAddonName);
//                        }


                        List<Map<String, Object>> options = (List<Map<String, Object>>) addonGroup.get("options");
                        if (options != null && !options.isEmpty()) {
                            // Вземи label на първата опция (вече преведен)
                            addonGroup.put("default", "0");
                        }

//                       BigDecimal rsPRice = new BigDecimal("0");
//                        if (options != null) {
//                            for (Map<String, Object> option : options) {
//                                String rawPrice = (String) option.get("price");
//                                if (rawPrice != null && !rawPrice.isEmpty()) {
//                                    // ПРЕВАЛУТИРАМЕ: Приемаме, че в сайта цената е била в EUR
//                                    BigDecimal eurPrice = new BigDecimal("5.10");
////                                    BigDecimal eurPrice = new BigDecimal(rawPrice);
//                                    if(rsPRice.compareTo(BigDecimal.ZERO) == 0){
//                                        rsPRice = currencyService.convert(eurPrice, "EUR", site.getCurrency().getCode());
//                                    }
//                                    // Записваме новата цена в обекта
//                                    option.put("price", rsPRice.toString());
//                                }
//
//
////                                String currentLabel = (String) option.get("label");
////                                if (currentLabel != null) {
////                                    product.getAddonConfig().stream()
////                                            .filter(config -> {
////                                                String bgLabel = config.getAddonValue().getTranslations().stream()
////                                                        .filter(t -> t.getLanguage().getCode().equals("bg"))
////                                                        .map(WpAddonValueTranslationEntity::getLabel)
////                                                        .findFirst().orElse("");
////                                                return bgLabel.equals(currentLabel);
////                                            })
////                                            .findFirst()
////                                            .ifPresent(config -> {
////                                                String translatedLabel = config.getAddonValue().getTranslations().stream()
////                                                        .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
////                                                        .map(WpAddonValueTranslationEntity::getLabel)
////                                                        .findFirst()
////                                                        .orElse(currentLabel); // fallback към оригинала
////                                                option.put("label", translatedLabel);
////                                            });
////                                }
//
//                            }
//                        }
                    }

                    // Добавяме превалутираните адони към тялото за обновяване
                    updateBody.put("addons", currentAddons);
                }


                Integer wpId = (Integer) searchResponse.get(0).get("id");
                restClient.patch()
                        .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/" + wpId)
                        .header("Authorization", "Basic " + auth)
                        .body(updateBody)
                        .retrieve()
                        .toBodilessEntity();
                log.info("успешно за продукт {}", product.getSku());

            } catch (Exception e) {
                log.error("Грешка при обновяване на сайт {}: {}, {}: {} {}", site.getUrl(), e.getMessage(), nameT, descriptionT, product.getSku());
            }

        }


    }


    @Transactional
    @Async
    public void deleteProductFromSites(String sku) {

        for (SiteEntity site : siteRepository.findByActiveTrue()) {
            try {
                String auth = Base64.getEncoder().encodeToString(
                        (site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes()
                );

                var searchResponse = restClient.get()
                        .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products?sku=" + sku)
                        .header("Authorization", "Basic " + auth)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                if (searchResponse != null && !searchResponse.isEmpty()) {
                    // Вземаме WordPress ID
                    Map<String, Object> wpProduct = searchResponse.get(0);
                    Integer wpProductId = (Integer) wpProduct.get("id");

                    // 2. Вземаме списъка със снимки от отговора на WordPress
                    List<Map<String, Object>> wpImages = (List<Map<String, Object>>) wpProduct.get("images");

                    if (wpImages != null && !wpImages.isEmpty()) {
                        Set<Long> mediaIds = wpImages.stream()
                                .map(img -> Long.valueOf(img.get("id").toString()))
                                .collect(Collectors.toSet());

                        // Използваме твоята логика за триене на медийни файлове
                        imageToWordPress.deleteMediaOneByOne(site, mediaIds, auth);
                        log.info("Изтрити са {} медийни файла от Media Library на {}", mediaIds.size(), site.getUrl());
                    }

                    // 3. Изтриваме продукта (force=true изтрива перманентно, без да го праща в кошчето)
                    restClient.delete()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/" + wpProductId + "?force=true")
                            .header("Authorization", "Basic " + auth)
                            .retrieve()
                            .toBodilessEntity();

                    log.info("Успешно изтрит продукт с SKU {} (WP ID: {}) от сайт {}",
                            sku, wpProductId, site.getUrl());

                    // 4. (ВАЖНО) Изтриваме мапингите за снимките за този сайт
                    // Тъй като продуктът вече го няма, не искаме ERP да мисли, че снимките са качени там
//                    if (product.getImages() != null) {
//                        for (WpProductImageEntity img : product.getImages()) {
//                            wpProductImageSiteMappingRepository.deleteByProductImageIdAndSite(img.getId(), site);
//                        }
//                    }
                } else {
                    log.info("Продукт с SKU {} не е намерен в сайт {}, прескачане...",
                            sku, site.getUrl());
                }

            } catch (Exception e) {
                log.error("Грешка при изтриване на продукт от сайт {}: {}", site.getUrl(), e.getMessage());
            }
        }


    }


    @Transactional
    @Async
    public void updateProductOnSitesOnlyNewSiteUpload(WpProductEntity product, Long lastEditedSiteId) throws InterruptedException {
        Thread.sleep(2000);
        product = wpProductRepository.findById(product.getId()).orElse(null);
        if(product == null) return;


        // --- КРИТИЧНА ЧАСТ: FORCE INITIALIZATION ---
        if (product.getAddonConfig() != null) {
            product.getAddonConfig().forEach(config -> {
                WpAddonValueEntity val = config.getAddonValue();
                val.getTranslations().size();

                if (val.getGroups() != null && !val.getGroups().isEmpty()) {
                    val.getGroups().forEach(group -> group.getTranslations().size());
                }
            });
        }
        // --- КРАЙ НА ИНИЦИАЛИЗАЦИЯТА ---


        List<SiteEntity> siteList;

//        if (lastEditedSiteId == null) {
//            siteList = siteRepository.findAll();
//        }
//        else {
            siteList = siteRepository.findById(lastEditedSiteId)
                    .map(List::of)
                    .orElse(Collections.emptyList());
//        }
            if(siteList.isEmpty() || siteList.get(0).getUrl().contains("sateno.bg")) {
                log.error("ERROR because there is selected sateno.bg");
                return;
            }
        for (SiteEntity site : siteList) {
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
                List<Map<String, Object>> currentMeta = new ArrayList<>();
                if(!isNewProduct) {
                    currentMeta = (List<Map<String, Object>>) searchResponse.get(0).get("meta_data");
                }

                Map<String, Object> updateBody = new HashMap<>();
                updateBody.put("stock_quantity", product.getStockQuantity());
                updateBody.put("manage_stock", product.getSaleType() != ProductSaleType.UNLIMITED);
                updateBody.put("backorders", product.getSaleType() == ProductSaleType.UNLIMITED? "yes": "no");
                updateBody.put("backorders_allowed", product.getSaleType() == ProductSaleType.UNLIMITED);
                updateBody.put("weight", product.getWeight());
                updateBody.put("status", product.getStatus().getValue());

                if(product.getSaleType() == ProductSaleType.UNLIMITED) {
                    updateBody.put("stock_status", "instock");
                }

                // 1. Опит за намиране на съществуващ превод за езика на сайта
                WpProductTranslationEntity translation = product.getTranslations().stream()
                        .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
                        .findFirst()
                        .orElse(null);

                if (translation == null || translation.getName().isEmpty()) {
                    log.info("Преводът липсва за SKU {} на език {}. Стартиране на ChatGPT превод...",
                            product.getSku(), site.getLanguage().getName());

                    WpProductTranslationEntity base = product.getTranslations().stream()
                            .filter(t -> t.getLanguage().getId() == 1L)
                            .findFirst()
                            .orElse(null);

                    String targetLang = site.getLanguage().getName();
                    String sourceLang = base.getLanguage().getName();

                    String namePrompt = String.format(
                            "Translate this e-commerce product name from %s to %s: '%s'. " +
                                    "IMPORTANT: Return ONLY the translated string. Do not include any quotes, explanations, or introductory text.",
                            sourceLang, targetLang, base.getName()
                    );
                    String translatedName = chatGptService.translateText(base.getName(), namePrompt);

                    String translatedShort = "";
                    if (base.getShortDescription() != null && !base.getShortDescription().isEmpty()) {
                        String shortPrompt = String.format("Translate this product short description from %s to %s. Keep it concise: '%s'",
                                sourceLang, targetLang, base.getShortDescription());
                        translatedShort = chatGptService.translateText(base.getShortDescription(), shortPrompt);
                    }

                    String translatedDesc = "";
                    if (base.getDescription() != null && !base.getDescription().isEmpty()) {
                        String descPrompt = String.format("Translate this product description from %s to %s. IMPORTANT: Preserve all HTML tags and structure exactly as they are.",
                                sourceLang, targetLang);
                        translatedDesc = chatGptService.translateText(base.getDescription(), descPrompt);
                    }

                    if(translation == null) {
                        translation = new WpProductTranslationEntity();
                    }
                    translation.setProduct(product);
                    translation.setLanguage(site.getLanguage());
                    translation.setName(translatedName);
                    translation.setShortDescription(translatedShort);
                    translation.setDescription(translatedDesc);

                    wpProductTranslationRepository.save(translation);
                    product.getTranslations().add(translation);

                    log.info("Успешен превод за SKU {} на език {}", product.getSku(), targetLang);
                }

                updateBody.put("name", translation.getName());
                updateBody.put("short_description", cleanHtml(translation.getShortDescription()));
                updateBody.put("description", cleanHtml(translation.getDescription()));


                // PRICE
                WpProductSiteConfigEntity currentSiteConfig = product.getSiteConfigs().stream()
                        .filter(c -> c.getSite().getId().equals(site.getId()))
                        .findFirst()
                        .orElse(null);
                // Ако нямаме конфигурация за този сайт, създаваме я
                boolean isNewConfig = false;
                if (currentSiteConfig == null) {
                    currentSiteConfig = new WpProductSiteConfigEntity();
                    currentSiteConfig.setProduct(product);
                    currentSiteConfig.setSite(site);
                    isNewConfig = true;
                }

                WpProductSiteConfigEntity baseConfig = product.getSiteConfigs().stream()
                        .filter(c -> c.getSite().getUrl().contains("sateno.bg"))
                        .findFirst()
                        .orElse(null);


                BigDecimal regularPrice = BigDecimal.ZERO;
                BigDecimal salePrice = null;

                if (baseConfig != null && baseConfig.getRegularPrice() != null && baseConfig.getRegularPrice().compareTo(BigDecimal.ZERO) > 0) {
                    String sourceCurrency = baseConfig.getSite().getCurrency().getCode();
                    String targetCurrency = site.getCurrency().getCode();

                    if (!sourceCurrency.equals(targetCurrency)) {
                        regularPrice = currencyService.convert(baseConfig.getRegularPrice(), sourceCurrency, targetCurrency);
                        salePrice = (baseConfig.getPrice() != null && baseConfig.getPrice().compareTo(BigDecimal.ZERO) > 0)
                                ? currencyService.convert(baseConfig.getPrice(), sourceCurrency, targetCurrency)
                                : null;
                    } else {
                        regularPrice = baseConfig.getRegularPrice();
                        salePrice = baseConfig.getPrice();
                    }
                } else {
                    log.warn("SKU {}: Липсва цена в базовата конфигурация (sateno.bg)! Продуктът ще се качи с цена 0.", product.getSku());
                }

                // ЗАПИСВАМЕ в нашата база данни
                currentSiteConfig.setRegularPrice(regularPrice);
                currentSiteConfig.setPrice(salePrice);

                if (isNewConfig) {
                    // Ако имаш wpProductSiteConfigRepository го ползвай тук:
                    // wpProductSiteConfigRepository.save(currentSiteConfig);
                    product.getSiteConfigs().add(currentSiteConfig);
                }

                // 3. Попълваме в заявката към WooCommerce
                if (regularPrice.compareTo(BigDecimal.ZERO) >= 0) {
                    updateBody.put("regular_price", regularPrice.toString());

                    if (salePrice != null && salePrice.compareTo(BigDecimal.ZERO) > 0) {
                        updateBody.put("sale_price", salePrice.toString());
                        updateBody.put("price", salePrice.toString());
                    } else {
                        updateBody.put("sale_price", "");
                        updateBody.put("price", regularPrice.toString());
                    }
                }





//                for (WpProductSiteConfigEntity siteConfig : product.getSiteConfigs()) {
//                    if(Objects.equals(siteConfig.getSite().getId(), site.getId())) {
//                        updateBody.put("price", siteConfig.getRegularPrice().toString());
//                        updateBody.put("regular_price", siteConfig.getRegularPrice().toString());
//                        String salePriceStr = (siteConfig.getPrice() != null && siteConfig.getPrice().compareTo(BigDecimal.ONE) >= 0)
//                                ? siteConfig.getPrice().toString()
//                                : "";
//
//                        updateBody.put("sale_price", salePriceStr);
//                    }
//                }

                // BRAND
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

                // CATEGORIES
                List<Map<String, Object>> categoriesList = new ArrayList<>();
                if (product.getCategories() != null) {
                    for (WpCategoryEntity category : product.getCategories()) {

                        Optional<WpCategorySiteMappingEntity> mappingOpt =
                                wpCategorySiteMappingRepository
                                        .findByWpCategoryAndSite(category, site);

                        if (mappingOpt.isPresent()) {
                            Long wpCategoryId = mappingOpt.get().getWpId();
                            Map<String, Object> categoryItem = new HashMap<>();
                            categoryItem.put("id", wpCategoryId);
                            categoriesList.add(categoryItem);
                        } else {
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

                                WpCategorySiteMappingEntity fallbackMapping = new WpCategorySiteMappingEntity();
                                fallbackMapping.setWpCategory(category);
                                fallbackMapping.setSite(site);
                                fallbackMapping.setWpId(wpCategoryId);
                                wpCategorySiteMappingRepository.save(fallbackMapping);
                                log.info("Запазен е липсващ мапинг за категория {} с WP_ID {}", category.getSlug(), wpCategoryId);
                            }
                        }
                    }
                }

                updateBody.put("categories", categoriesList);

                // --- ЛОГИКА ЗА СНИМКИТЕ: ИЗПОЛЗВАЙКИ ТВОЯ МАПИНГ ---
                List<Map<String, Object>> imageList = new ArrayList<>();
                Map<String, Object> finalVideoGalleryMap = new HashMap<>();

                if (product.getImages() != null) {
                    // 1. Намираме SiteEntity за sateno.bg (можеш да го кешираш или търсиш по URL)
                    SiteEntity satenoSite = siteRepository.findAll().stream()
                            .filter(s -> s.getUrl().contains("sateno.bg"))
                            .findFirst()
                            .orElse(null);

                    if (satenoSite != null) {
                        // Филтрираме снимките, които имат мапинг към sateno.bg
                        List<WpProductImageEntity> satenoMedia = product.getImages().stream()
                                .filter(img -> wpProductImageSiteMappingRepository
                                        .findByProductImageIdAndSite(img.getId(), satenoSite).isPresent())
                                .toList();

                        List<WpProductImageEntity> erpVideos = satenoMedia.stream()
                                .filter(WpProductImageEntity::isVideo)
                                .toList();

                        // 2. Сортиране (същото като досега)
                        List<WpProductImageEntity> sortedImages = satenoMedia.stream()
                                .filter(e -> !e.isVideo())
                                .sorted((a, b) -> {
                                    boolean aIsPrimary = Boolean.TRUE.equals(a.getIsPrimary());
                                    boolean bIsPrimary = Boolean.TRUE.equals(b.getIsPrimary());
                                    if (aIsPrimary && !bIsPrimary) return -1;
                                    if (!aIsPrimary && bIsPrimary) return 1;
                                    return 0;
                                })
                                .toList();

                        // 3. ОБРАБОТКА И ЗАПИС НА МАПИНГ ЗА НОВИЯ САЙТ
                        int currentOrderIndex = 0;
                        for (WpProductImageEntity imgEntity : sortedImages) {

                            // Проверяваме дали ИМА мапинг за НОВИЯ сайт
                            Optional<WpProductImageSiteMappingEntity> mappingOpt = wpProductImageSiteMappingRepository
                                    .findByProductImageIdAndSite(imgEntity.getId(), site);

                            Long wpMediaIdForSite;

                            if (mappingOpt.isPresent()) {
                                wpMediaIdForSite = mappingOpt.get().getWpMediaId();
                            } else {
                                // Ако няма - качваме я като нова за този сайт
                                log.info("Качване на снимка от sateno.bg към нов сайт {}: {}", site.getUrl(), imgEntity.getLocalSrc());
                                wpMediaIdForSite = imageToWordPress.uploadImageToWordPress(site, imgEntity.getLocalSrc());

                                if (wpMediaIdForSite != null) {
                                    WpProductImageSiteMappingEntity newMapping = new WpProductImageSiteMappingEntity();
                                    newMapping.setWpMediaId(wpMediaIdForSite);
                                    newMapping.setSite(site);
                                    newMapping.setProductImage(imgEntity);
                                    newMapping.setOrderIndex(currentOrderIndex);
                                    wpProductImageSiteMappingRepository.save(newMapping);
                                }
                            }

                            if (wpMediaIdForSite != null) {
                                Map<String, Object> imgMap = new HashMap<>();
                                imgMap.put("id", wpMediaIdForSite);
                                imageList.add(imgMap);
                            }
                            currentOrderIndex++;
                        }

                        // 4. ВИДЕА (аналогично)
                        for (WpProductImageEntity video : erpVideos) {
                            if (video.getParent() == null) continue;

                            Optional<WpProductImageSiteMappingEntity> videoMappingOpt = wpProductImageSiteMappingRepository
                                    .findByProductImageIdAndSite(video.getId(), site);

                            Long wpVideoId;
                            String wpVideoUrl;

                            if (videoMappingOpt.isPresent()) {
                                wpVideoId = videoMappingOpt.get().getWpMediaId();
                                wpVideoUrl = videoMappingOpt.get().getWpUrl();
                            } else {
                                log.info("🎬 Качване на видео към нов сайт {}: {}", site.getUrl(), video.getLocalSrc());
                                Map<String, Object> uploadResult = imageToWordPress.uploadVideoToWordPress(site, video.getLocalSrc());
                                wpVideoId = Long.valueOf(uploadResult.get("id").toString());
                                wpVideoUrl = uploadResult.get("url").toString();

                                WpProductImageSiteMappingEntity newVideoMapping = new WpProductImageSiteMappingEntity();
                                newVideoMapping.setWpMediaId(wpVideoId);
                                newVideoMapping.setSite(site);
                                newVideoMapping.setProductImage(video);
                                newVideoMapping.setWpUrl(wpVideoUrl);
                                wpProductImageSiteMappingRepository.save(newVideoMapping);
                            }

                            // (Логиката за прикачване към галерията остава същата)
                            // ...
                        }
                    }
                }
                updateBody.put("images", imageList);

                currentMeta.removeIf(meta -> "woodmart_wc_video_gallery".equals(meta.get("key")));
                Map<String, Object> videoMetaEntry = new HashMap<>();
                videoMetaEntry.put("key", "woodmart_wc_video_gallery");
                videoMetaEntry.put("value", finalVideoGalleryMap);
                currentMeta.add(videoMetaEntry);
                updateBody.put("meta_data", currentMeta);


                CurrencyEntity currency = site.getCurrency();

                // 7. ADDONS
                List<Map<String, Object>> wooAddons = generateWooAddons(product, site, currency);
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
                        Set<Long> idsToDeleteFromMediaLibrary = wpImagesFromSearch.stream()
                                .map(img -> Long.valueOf(img.get("id").toString()))
                                .filter(id -> !erpWpMediaIds.contains(id))
                                .collect(Collectors.toSet());

                        if (!idsToDeleteFromMediaLibrary.isEmpty()) {
                            imageToWordPress.deleteMediaOneByOne(site, idsToDeleteFromMediaLibrary, auth);
                            log.info("Изтрити са {} излишни медийни файла от сайт {}", idsToDeleteFromMediaLibrary.size(), site.getUrl());
                        }
                    }
                }

            } catch (Exception e) {
                log.error("Грешка при обновяване на сайт {}: {}", site.getUrl(), e.getMessage());
            }
        }

    }

    @Transactional
    @Async
    public void syncImagesFromSite6ToSite(WpProductEntity product, Long targetSiteId) {
        product = wpProductRepository.findById(product.getId()).orElse(null);
        if (product == null) return;

        SiteEntity targetSite = siteRepository.findById(targetSiteId).orElse(null);
        SiteEntity site6 = siteRepository.findById(6L).orElse(null);
        if (targetSite == null || site6 == null || targetSite.getUrl().contains("sateno.bg")) {
            log.error("ERROR because there is selected sateno.bg | or targetSite == null | site6 == null");
            return;
        };

        String auth = Base64.getEncoder().encodeToString(
                (targetSite.getConsumerKey() + ":" + targetSite.getConsumerSecret()).getBytes()
        );

        try {
            // 1. Намираме продукта в целевия сайт
            var searchResponse = restClient.get()
                    .uri(targetSite.getUrlWithHttps() + "/wp-json/wc/v3/products?sku=" + product.getSku())
                    .header("Authorization", "Basic " + auth)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (searchResponse == null || searchResponse.isEmpty()) {
                log.warn("syncImagesFromSite6: продукт {} не е намерен в {}", product.getSku(), targetSite.getUrl());
                return;
            }

            Map<String, Object> wpProduct = searchResponse.get(0);
            Integer wpProductId = (Integer) wpProduct.get("id");

            // 2. Изтриваме всички снимки от целевия сайт
            List<Map<String, Object>> wpImages = (List<Map<String, Object>>) wpProduct.get("images");
            if (wpImages != null && !wpImages.isEmpty()) {
                Set<Long> mediaIds = wpImages.stream()
                        .map(img -> Long.valueOf(img.get("id").toString()))
                        .collect(Collectors.toSet());
                imageToWordPress.deleteMediaOneByOne(targetSite, mediaIds, auth);
            }

            // 3. Намираме снимките от сайт 6 за този продукт
            List<WpProductImageEntity> productImages = product.getImages();
            if (productImages == null || productImages.isEmpty()) {
                log.error("ERROR productImages are null or empty");
                return;
            };

            List<Map<String, Object>> imageList = new ArrayList<>();

            for (WpProductImageEntity imgEntity : productImages) {
                if (imgEntity.isVideo()) continue;

                // Проверяваме дали снимката има mapping за сайт 6
                Optional<WpProductImageSiteMappingEntity> site6Mapping =
                        wpProductImageSiteMappingRepository.findByProductImageIdAndSite(imgEntity.getId(), site6);
                if (site6Mapping.isEmpty()) continue;

                // 4. Качваме снимката в целевия сайт (или ползваме вече качена)
                Optional<WpProductImageSiteMappingEntity> existingMapping =
                        wpProductImageSiteMappingRepository.findByProductImageIdAndSite(imgEntity.getId(), targetSite);

                Long wpMediaId;
                if (existingMapping.isPresent() && existingMapping.get().getWpMediaId() != null) {
                    wpMediaId = existingMapping.get().getWpMediaId();
                } else {
                    wpMediaId = imageToWordPress.uploadImageToWordPress(targetSite, imgEntity.getLocalSrc());
                    if (wpMediaId != null) {
                        WpProductImageSiteMappingEntity newMapping = new WpProductImageSiteMappingEntity();
                        newMapping.setProductImage(imgEntity);
                        newMapping.setSite(targetSite);
                        newMapping.setWpMediaId(wpMediaId);
                        newMapping.setOrderIndex(site6Mapping.get().getOrderIndex());
                        wpProductImageSiteMappingRepository.save(newMapping);
                    }
                }

                if (wpMediaId != null) {
                    Map<String, Object> imgMap = new HashMap<>();
                    imgMap.put("id", wpMediaId);
                    imageList.add(imgMap);
                }
            }

            // 5. Закачаме снимките към продукта в целевия сайт
            Map<String, Object> updateBody = new HashMap<>();
            updateBody.put("images", imageList);

            restClient.patch()
                    .uri(targetSite.getUrlWithHttps() + "/wp-json/wc/v3/products/" + wpProductId)
                    .header("Authorization", "Basic " + auth)
                    .body(updateBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("syncImagesFromSite6: закачени {} снимки към {} за продукт {}",
                    imageList.size(), targetSite.getUrl(), product.getSku());

        } catch (Exception e) {
            log.error("syncImagesFromSite6: грешка за продукт {}: {}", product.getSku(), e.getMessage());
        }
    }
}
