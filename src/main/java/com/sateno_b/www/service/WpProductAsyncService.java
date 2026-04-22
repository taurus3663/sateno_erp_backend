package com.sateno_b.www.service;

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
        Thread.sleep(2000);
        product = wpProductRepository.findById(product.getId()).orElse(null);
        if(product == null) return;


        // --- КРИТИЧНА ЧАСТ: FORCE INITIALIZATION ---
        // Форсираме зареждането на адоните и ВСИЧКИ техни преводи за ВСИЧКИ езици
        if (product.getAddonConfig() != null) {
            product.getAddonConfig().forEach(config -> {
                // Инициализираме стойността на адона
                WpAddonValueEntity val = config.getAddonValue();
                val.getTranslations().size(); // Зарежда преводите на стойността (напр. "Червен")

                // Инициализираме групата и нейните преводи (напр. "Цвят")
                if (val.getGroups() != null && !val.getGroups().isEmpty()) {
                    val.getGroups().forEach(group -> group.getTranslations().size());
                }
            });
        }
        // --- КРАЙ НА ИНИЦИАЛИЗАЦИЯТА ---


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

                if(product.getSaleType() == ProductSaleType.UNLIMITED) {
                    updateBody.put("stock_status", "instock");
                }

//                DESCRIPTION
//                for (WpProductTranslationEntity translation : product.getTranslations()) {
//                    if(translation.getLanguage() == site.getLanguage()) {
//                        updateBody.put("short_description", translation.getShortDescription());
//                        updateBody.put("description", translation.getDescription());
//                        updateBody.put("name", translation.getName());
//                        break;
//                    }
//                }

                // 1. Опит за намиране на съществуващ превод за езика на сайта
                WpProductTranslationEntity translation = product.getTranslations().stream()
                        .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
                        .findFirst()
                        .orElse(null);

                if (translation == null || translation.getName().isEmpty()) {
                    log.info("Преводът липсва за SKU {} на език {}. Стартиране на ChatGPT превод...",
                            product.getSku(), site.getLanguage().getName());

                    // Вземаме изходния текст (обикновено първия наличен превод, напр. Български)
                    // НАМИРАМЕ БЪЛГАРСКИЯ ПРЕВОД (id: 1) КАТО ИЗТОЧНИК
                    WpProductTranslationEntity base = product.getTranslations().stream()
                            .filter(t -> t.getLanguage().getId() == 1L) // Тук приемаме, че BG ID е 1
                            .findFirst()
                            .orElse(null);

                    String targetLang = site.getLanguage().getName();
                    String sourceLang = base.getLanguage().getName();

                    // Превод на Името
                    String namePrompt = String.format(
                            "Translate this e-commerce product name from %s to %s: '%s'. " +
                                    "IMPORTANT: Return ONLY the translated string. Do not include any quotes, explanations, or introductory text.",
                            sourceLang, targetLang, base.getName()
                    );
                    String translatedName = chatGptService.translateText(base.getName(), namePrompt);

                    // Превод на Краткото описание
                    String translatedShort = "";
                    if (base.getShortDescription() != null && !base.getShortDescription().isEmpty()) {
                        String shortPrompt = String.format("Translate this product short description from %s to %s. Keep it concise: '%s'",
                                sourceLang, targetLang, base.getShortDescription());
                        translatedShort = chatGptService.translateText(base.getShortDescription(), shortPrompt);
                    }

                    // Превод на Дългото описание (с внимание към HTML)
                    String translatedDesc = "";
                    if (base.getDescription() != null && !base.getDescription().isEmpty()) {
                        String descPrompt = String.format("Translate this product description from %s to %s. IMPORTANT: Preserve all HTML tags and structure exactly as they are.",
                                sourceLang, targetLang);
                        translatedDesc = chatGptService.translateText(base.getDescription(), descPrompt);
                    }

                    // 2. Запис в базата данни, за да не се превежда отново при следващ синк
                    if(translation == null) {
                        translation = new WpProductTranslationEntity();
                    }
                    translation.setProduct(product);
                    translation.setLanguage(site.getLanguage());
                    translation.setName(translatedName);
                    translation.setShortDescription(translatedShort);
                    translation.setDescription(translatedDesc);

                    wpProductTranslationRepository.save(translation);

                    // Добавяме го в списъка на обекта в паметта, за да го ползваме веднага
                    product.getTranslations().add(translation);

                    log.info("Успешен превод за SKU {} на език {}", product.getSku(), targetLang);
                }

// 3. Попълваме тялото на заявката към WooCommerce
                updateBody.put("name", translation.getName());
                updateBody.put("short_description", cleanHtml(translation.getShortDescription()));
                updateBody.put("description", cleanHtml(translation.getDescription()));



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
// --- ОБНОВЕНА ЛОГИКА ЗА ЦЕНИ ---

// 1. Намираме базовата конфигурация от sateno.bg (за справка)
//                WpProductSiteConfigEntity bgConfig = product.getSiteConfigs().stream()
//                        .filter(c -> c.getSite().getUrl().contains("sateno.bg"))
//                        .findFirst()
//                        .orElse(null);
//
//                BigDecimal fallbackRegular = (bgConfig != null) ? bgConfig.getRegularPrice() : BigDecimal.ZERO;
//                BigDecimal fallbackSale = (bgConfig != null) ? bgConfig.getPrice() : null;
//
//// 2. Намираме конфигурацията за текущия сайт в цикъла
//                WpProductSiteConfigEntity currentSiteConfig = product.getSiteConfigs().stream()
//                        .filter(sc -> sc.getSite().getId().equals(site.getId()))
//                        .findFirst()
//                        .orElse(null);
//
//// Ако за текущия сайт изобщо липсва конфигурация в базата, създаваме я
//                if (currentSiteConfig == null) {
//                    currentSiteConfig = new WpProductSiteConfigEntity();
//                    currentSiteConfig.setProduct(product);
//                    currentSiteConfig.setSite(site);
//                    product.getSiteConfigs().add(currentSiteConfig);
//                }
//
//// 3. ПРОВЕРКА И РЕМОНТ: Ако цената е 0 или null, взимаме тази от sateno.bg
//                if (currentSiteConfig.getRegularPrice() == null || currentSiteConfig.getRegularPrice().compareTo(BigDecimal.ZERO) <= 0) {
//                    log.info("SKU {}: Коригирам цена за {}, ползвам фалбек от sateno.bg: {}",
//                            product.getSku(), site.getUrl(), fallbackRegular);
//
//                    currentSiteConfig.setRegularPrice(fallbackRegular);
//                    currentSiteConfig.setPrice(fallbackSale);
//                    // Благодарение на @Transactional, промяната ще се запише в БД автоматично в края
//                }
//
//// 4. Попълваме updateBody за WooCommerce
//                updateBody.put("regular_price", currentSiteConfig.getRegularPrice().toString());
//
//                String salePriceStr = (currentSiteConfig.getPrice() != null && currentSiteConfig.getPrice().compareTo(BigDecimal.ONE) >= 0)
//                        ? currentSiteConfig.getPrice().toString()
//                        : "";
//                updateBody.put("sale_price", salePriceStr);
//
//// Основната цена в WP (price) трябва да е активната (промоционалната или редовната)
//                updateBody.put("price", !salePriceStr.isEmpty() ? salePriceStr : currentSiteConfig.getRegularPrice().toString());



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

                    // СОРТИРАНЕ: Слагаме Primary снимката най-отпред
                    List<WpProductImageEntity> sortedImages = product.getImages().stream()
                            .sorted((a, b) -> Boolean.compare(b.getIsPrimary(), a.getIsPrimary()))
                            .toList();



                    for (WpProductImageEntity imgEntity : sortedImages) {

                        // 1. Търсим съществуващ мапинг за текущия сайт
                        Optional<WpProductImageSiteMappingEntity> mappingOpt = wpProductImageSiteMappingRepository
                                .findByProductImageIdAndSite(imgEntity.getId(), site);

                        if (mappingOpt.isPresent()) {
                            // Снимката вече съществува в този сайт - добавяме я в списъка на продукта
                            Map<String, Object> imgMap = new HashMap<>();
                            imgMap.put("id", mappingOpt.get().getWpMediaId());
                            imageList.add(imgMap);
                        } else {
                            // 2. КЛЮЧОВАТА ПРОВЕРКА: Качваме снимката САМО ако тя е "нова" за системата
                            // Проверяваме дали снимката има мапинги изобщо.
                            // Ако НЯМА никакви мапинги, значи е току-що качена от Angular и чака в temp.

                            boolean isBrandNewImage = imgEntity.getSiteMappings() == null || imgEntity.getSiteMappings().isEmpty();

                            if (isBrandNewImage) {
                                log.info("Качване на НОВА снимка към сайт {}: {}", site.getUrl(), imgEntity.getLocalSrc());
                                Long wpMediaId = imageToWordPress.uploadImageToWordPress(site, imgEntity.getLocalSrc());

                                if (wpMediaId != null) {
                                    WpProductImageSiteMappingEntity newMapping = new WpProductImageSiteMappingEntity();
                                    newMapping.setWpMediaId(wpMediaId);
                                    newMapping.setSite(site);
                                    newMapping.setProductImage(imgEntity);
                                    wpProductImageSiteMappingRepository.save(newMapping);

                                    Map<String, Object> imgMap = new HashMap<>();
                                    imgMap.put("id", wpMediaId);
                                    imageList.add(imgMap);
                                }
                            } else {
                                // Снимката има мапинги за други сайтове, но не и за този.
                                // Тъй като не е "нова" (temp), ние НЕ я качваме тук автоматично.
                                log.info("Снимка {} е локална за друг сайт. Пропускам качване в {}.",
                                        imgEntity.getId(), site.getUrl());
                            }
                        }
                    }
                }
//                                if(!imageList.isEmpty()) {
                    updateBody.put("images", imageList);
//                }


                CurrencyEntity currency = site.getCurrency();

                // 7. ADDONS (FORCE GENERATION)
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
}
