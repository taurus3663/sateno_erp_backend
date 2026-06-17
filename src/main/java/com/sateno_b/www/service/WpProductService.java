package com.sateno_b.www.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.CustomUserDetails;
import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.entity.interfaces.WpProductMinified;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.enums.ProductSaleType;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.shared.AuthTool;
import com.sateno_b.www.shared.SlugTool;
import com.sateno_b.www.shared.ImageToWordPress;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WpProductService {

    private final SiteRepository siteRepository;
    private final RestClient restClient;
    private final WpProductRepository wpProductRepository;
    private final WpProductImageRepository wpProductImageRepository;
    private final WpProductAddonConfigRepository wpProductAddonConfigRepository;
    private final WpProductImageSiteMappingRepository wpProductImageSiteMappingRepository;
    private final WpProductTranslationRepository wpProductTranslationRepository;
    private final WpBrandRepository wpBrandRepository;
    private final WpCategoryRepository wpCategoryRepository;
    private final WpAddonRepository wpAddonRepository;
    private final WpAddonValueRepository wpAddonValueRepository;
    private final WpAddonTranslationRepository wpAddonTranslationRepository;
    private final WpAddonValueTranslationRepository wpAddonValueTranslationRepository;
    private final WpProductSiteConfigRepository wpProductSiteConfigRepository;
    private final ModelMapper modelMapper;
    private final FileStorageService fileStorageService;
    private final LanguageRepository languageRepository;
    @PersistenceContext
    private final EntityManager entityManager;
    private final WpProductAsyncService  wpProductAsyncService;

    private static final String PRODUCTS_URL = "/wp-json/wc/v3/products";
    private final WpProductHistoryRepository wpProductHistoryRepository;
    private final ImageToWordPress imageToWordPress;
    private final ChatGptService chatGptService;
    private final WpOrderRepository wpOrderRepository;
    private final CurrencyService currencyService;
    private final SchemeWpProductRepository schemeWpProductRepository;
    private final UserRepository userRepository;
    private final WpCategoryAsyncService wpCategoryAsyncService;
    private final WpBrandAsyncService wpBrandAsyncService;
    private final WpCategorySiteMappingRepository wpCategorySiteMappingRepository;
    private final WpProductCleanupService wpProductCleanupService;
    private final WpAttributeValueRepository wpAttributeValueRepository;


    @Transactional
//    @CacheEvict(value = "productsList", allEntries = true)
    public void syncProductsToDB(Long siteId) {
        SiteEntity site = siteRepository.findById(siteId).orElseThrow();
//        String auth = AuthTool.getAuth(site.getConsumerKey(), site.getConsumerSecret());
        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
        LanguageEntity language = site.getLanguage();

        // get All Products
        List<WooProductDto> Products = fetchAllProducts(site, auth);
        int count = 0;
        for (WooProductDto product : Products) {
            try {

                processSingleProduct(product, site, language);

                if(++count == 50) {
                    count = 0;
                    wpProductRepository.flush();
//                    wpProductTranslationRepository.flush();
//                    wpProductSiteConfigRepository.flush();
                    wpProductImageSiteMappingRepository.flush();
//                    wpAddonRepository.flush();
                    entityManager.clear();
                }

            } catch (Exception e) {
                log.error("грешка при обработка на продукт SKU: {}, грешка: {}", product.getSku(), e.getMessage());
            }
        }

    }

//    sinhronizirane na imena i otnosno na produkti
    @Transactional
    public void syncProductNAnInfo(Long siteId, List<String> skuList) {
//        SiteEntity site = siteRepository.findById(siteId).orElseThrow();
//        if(site.getUrl().contains("sateno.bg")) {
//            throw new RuntimeException("sateno.bg");
//        }

        int count = 0;
        List<WpProductEntity> productList;

        if(skuList.isEmpty()){
            productList = wpProductRepository.findAll();
        } else {
            productList = wpProductRepository.findAllBySkuIn(skuList);
        }

        for (WpProductEntity product : productList) {
            try {
                log.info(String.valueOf(++count));
//                wpProductAsyncService.updateProductNameAnInfo(product, siteId);
                wpProductAsyncService.syncImagesFromSite6ToSite(product, siteId);
//                   wpProductAsyncService.updateProductOnSitesOnlyPrices(product, siteId);
//                   log.info("Успешно създаден нов продукт с SKU {} в сайт {}", product.getSku(), site.getUrl());
            } catch (Exception e) {
                log.error("Критична грешка при масова синхронизация на SKU {}: {}", product.getSku(), e.getMessage());
            }
//            });
        }
        System.out.println("УСПЕШНО ПРИКЛЮЧЕН СИНХ");
        log.info("УСПЕШНО ПРИКЛЮЧЕН СИНХ");



    }

//    @Transactional
    public void syncProductsToSite(Long siteId) {
        SiteEntity site = siteRepository.findById(siteId).orElseThrow();
        if(site.getUrl().contains("sateno.bg")) {
         throw new RuntimeException("sateno.bg");
        }

        try { wpProductCleanupService.clearAllProductsFromSite(site); } catch (Exception e) { log.error("Грешка при чистене на продукти: {}", e.getMessage()); return; }
        try { wpProductCleanupService.clearAllCategoriesFromSite(site); } catch (Exception e) { log.error("Грешка при чистене на категории: {}", e.getMessage()); return; }
        try { wpProductCleanupService.clearAllBrandsFromSite(site); } catch (Exception e) { log.error("Грешка при чистене на брандове: {}", e.getMessage()); return; }

//        populate Categories
       try {
           CompletableFuture<Boolean> categoryCompletableFuture = wpCategoryAsyncService.syncWpCategoryToSite(siteId);
           CompletableFuture<Boolean> brandCompletableFuture = wpBrandAsyncService.syncAllBrandsToSite(siteId);

           CompletableFuture.allOf(categoryCompletableFuture, brandCompletableFuture).join();

           if (!categoryCompletableFuture.get() || !brandCompletableFuture.get()) {
               return;
           }
       } catch (Exception e) {
           log.error("Грешка при синхронизация на категории и брандове: {}", e.getMessage());
           return;
       }

        // ТУК добави малка пауза или flush, за да си сигурен че всичко е commited
        // Не е задължително ако горните транзакции са приключили, но е застраховка:
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        List<WpProductEntity> productList = wpProductRepository.findAll();
//        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger count = new AtomicInteger();
        for (WpProductEntity product : productList) {
//            executor.submit(() -> {
//                count.getAndIncrement();
//                if(count.get() == 10) {
//                    return;
//                }
               try {
                   wpProductAsyncService.updateProductOnSitesOnlyNewSiteUpload(product, siteId);
//                   wpProductAsyncService.updateProductOnSitesOnlyPrices(product, siteId);
//                   log.info("Успешно създаден нов продукт с SKU {} в сайт {}", product.getSku(), site.getUrl());
               } catch (Exception e) {
                   log.error("Критична грешка при масова синхронизация на SKU {}: {}", product.getSku(), e.getMessage());
               }
//            });
        }
        System.out.println("УСПЕШНО ПРИКЛЮЧЕН СИНХ");
//        executor.shutdown();
    }


    private List<Map<String, Object>> generateWooAddons(WpProductEntity product, SiteEntity site) {
        List<Map<String, Object>> wooAddons = new ArrayList<>();

        if (product.getAddonConfig() == null || product.getAddonConfig().isEmpty()) {
            return wooAddons;
        }

        // 1. Сортиране и Групиране
        List<WpProductAddonConfigEntity> sortedConfigs = product.getAddonConfig().stream()
                .sorted(Comparator.comparing(BaseEntity::getId))
                .collect(Collectors.toList());

        Map<String, List<WpProductAddonConfigEntity>> groupedAddons = new LinkedHashMap<>();

        for (WpProductAddonConfigEntity conf : sortedConfigs) {
            WpAddonEntity group = conf.getAddonValue().getGroups().get(0);

            // Превод на името на групата (напр. "Размер")
            String groupName = group.getTranslations().stream()
                    .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
                    .map(WpAddonTranslationEntity::getName)
                    .findFirst()
                    .orElseGet(() -> group.getTranslations().stream()
                            .filter(t -> t.getLanguage().getCode().equals("bg"))
                            .map(WpAddonTranslationEntity::getName)
                            .findFirst()
                            .orElse(group.getSlug()));

            groupedAddons.computeIfAbsent(groupName, k -> new ArrayList<>()).add(conf);
        }

        // 2. Генериране на JSON структурата
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
                // Превод на етикета (напр. "XL")
                String label = config.getAddonValue().getTranslations().stream()
                        .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
                        .map(WpAddonValueTranslationEntity::getLabel)
                        .findFirst()
                        .orElseGet(() -> config.getAddonValue().getTranslations().stream()
                                .filter(t -> t.getLanguage().getCode().equals("bg"))
                                .map(WpAddonValueTranslationEntity::getLabel)
                                .findFirst()
                                .orElse(config.getAddonValue().getSlug()));

                Map<String, Object> option = new HashMap<>();
                option.put("label", label);
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
        return wooAddons;
    }

    private List<WooProductDto> fetchAllProducts(SiteEntity site, String auth) {

        List<WooProductDto> products = new ArrayList<>();
        int currentPage = 1;
        int totalPages = 1;

        do {
            var response = restClient.get()
                    .uri(site.getUrlWithHttps() + PRODUCTS_URL + "?per_page=100&page=" + currentPage + "&orderby=id&order=asc")
//                    .uri(site.getUrl() + PRODUCTS_URL + "?per_page=100&page=" + currentPage + "&orderby=id&order=asc&sku=a1000")
                    .header("Authorization", "Basic " + auth)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<WooProductDto>>() {});

            if(response.getBody() != null) {
                products.addAll(response.getBody());
            }

            String totalPagesHeader = response.getHeaders().getFirst("X-WP-TotalPages");
            if (totalPagesHeader != null) {
                totalPages = Integer.parseInt(totalPagesHeader);
            }

            currentPage++;
        } while (currentPage <= totalPages);

        return products;
    }

    private void processSingleProduct(WooProductDto dto, SiteEntity site, LanguageEntity lang) {


    // 1. Намираме или създаваме Глобалния продукт по SKU
//        WpProductEntity product = wpProductRepository.findBySkuAndSite(dto.getSku(), site.getId())
//                .orElseGet(() -> wpProductRepository.save(new WpProductEntity()));

        Optional<WpProductEntity> existingProduct = wpProductRepository.findBySkuAndSite(dto.getSku(), site.getId());
        if (existingProduct.isPresent()) {
//            return;
            syncImagesForProduct(existingProduct.get(), dto.getImages(), site);
        }

//        WpProductEntity product = new WpProductEntity();
//        // 2. Обновяваме Глобалните данни (технически)
//        product.setStockQuantity(dto.getStock_quantity() == null? 0: dto.getStock_quantity());
//        product.setWeight(dto.getWeight());
//        product.setStatus(dto.getStatus());
//        product.setSaleType(dto.isManage_stock() ? ProductSaleType.LIMITED: ProductSaleType.UNLIMITED);
//        product.setManage_stock(dto.isManage_stock());
//        product.setSku(dto.getSku());
//        product.setStock_status(dto.getStock_status());
//        product.setType(dto.getType());
//        product.setFeatured(dto.isFeatured());
//        product.setCatalog_visibility(dto.getCatalog_visibility());
//        product.setDimensions(dto.getDimensions());
//        // 3. Свързваме Бранд (вече синхронизиран)
//        if (dto.getBrands() != null && !dto.getBrands().isEmpty()) {
//            wpBrandRepository.findBySlug(SlugTool.decodeSlug(dto.getBrands().get(0).getSlug()))
//                    .ifPresent(product::setBrand);
//        }
//
//        // 4. Свързваме Категории и Подкатегории (ManyToMany)
//        if (dto.getCategories() != null) {
//            product.getCategories().clear(); // Махаме старите, слагаме новите от WP
//            for (WooProductCategoryDto catDto : dto.getCategories()) {
//                wpCategoryRepository.findBySlug(SlugTool.decodeSlug(catDto.getSlug()))
//                        .ifPresent(product.getCategories()::add);
//            }
//        }
//
//        product = wpProductRepository.save(product);

        // 5. ЗАПИС НА ЦЕНИ И ТЕКСТОВЕ (Translation)
//        updateTranslation(product, dto, site, lang);

        // 5a запис на цени
//        updateSiteConfig(product, dto, site);

        // 6. СНИМКИ (Локално сваляне)
//        syncImagesForProduct(existingProduct.get(), dto.getImages(), site);

        // 7. АДОНИ (Специфични за продукта и сайта)
//        syncAddonsForProduct(product, dto.getAddons(), site, lang);

    }

    private void updateTranslation(WpProductEntity product, WooProductDto dto, SiteEntity site, LanguageEntity lang) {
        // Търсим съществуващ превод за този продукт на този сайт и език
        WpProductTranslationEntity translation = wpProductTranslationRepository
                .findByProductAndLanguage(product, lang)
                .orElse(new WpProductTranslationEntity());

        translation.setProduct(product);
//        translation.setSite(site);
        translation.setLanguage(lang);

        // Данни от WooCommerce
//        translation.setWpProductId(dto.getId());
        translation.setName(dto.getName());
//        translation.setSku(dto.getSku());
//        translation.setSlug(SlugTool.decodeSlug(dto.getSlug()));

        // Преобразуваме цените (ако са празни в WP, слагаме 0)
//        translation.setPrice(parsePrice(dto.getPrice()));
//        translation.setRegularPrice(parsePrice(dto.getRegular_price()));

        translation.setDescription(dto.getDescription());
        translation.setShortDescription(dto.getShort_description());
        wpProductTranslationRepository.save(translation);
    }

    private void updateSiteConfig(WpProductEntity product, WooProductDto dto, SiteEntity site) {
        // Търсим конфигурация за този продукт и този сайт
        WpProductSiteConfigEntity config = wpProductSiteConfigRepository
                .findByProductAndSite(product, site)
                .orElse(new WpProductSiteConfigEntity());

        config.setProduct(product);
        config.setSite(site);

        // Специфични данни за сайта от WooCommerce
        config.setPrice(parsePrice(dto.getPrice()));
        config.setRegularPrice(parsePrice(dto.getRegular_price()));
        config.setSalePrice(parsePrice(dto.getSale_price()));
        config.setSlug(SlugTool.decodeSlug(dto.getSlug()));
        config.setWpProductId(dto.getId());

        wpProductSiteConfigRepository.save(config);
    }

    private void syncImagesForProduct(WpProductEntity product, List<WooProductImageDto> wooImages, SiteEntity site) {
        if (wooImages == null) return;

        for (WooProductImageDto wooImg : wooImages) {
            // Проверяваме дали тази снимка (по WP Media ID) вече е свалена за ТОЗИ сайт
//            boolean alreadyExists = wpProductImageSiteMappingRepository.existsByWpMediaIdAndSite(wooImg.getId(), site);


//            if (!alreadyExists) {


                try {
                    // 1. Теглим байтовете на снимката
                    byte[] imageBytes = restClient.get()
                            .uri(wooImg.getSrc())
                            .retrieve()
                            .body(byte[].class);


                    if (imageBytes != null) {
                        // 2. Записваме на диска чрез FileStorageService
                        String localPath = FileStorageService.saveProductImage(imageBytes, product.getId(), wooImg.getId(), wooImg.getSrc());

                        // 3. Създаваме глобален обект за снимката
                        WpProductImageEntity imageEntity = new WpProductImageEntity();
                        imageEntity.setProduct(product);
                        imageEntity.setLocalSrc(localPath);
                        imageEntity = wpProductImageRepository.save(imageEntity);

                        // 4. Създаваме мапинг за конкретния сайт (за да знаем, че вече я имаме)
                        WpProductImageSiteMappingEntity mapping = new WpProductImageSiteMappingEntity();
                        mapping.setProductImage(imageEntity);
                        mapping.setSite(site);
                        mapping.setWpMediaId(wooImg.getId());
                        mapping.setWpUrl(wooImg.getSrc());
                        wpProductImageSiteMappingRepository.save(mapping);
                    }
                } catch (Exception e) {
                    log.error("Грешка при теглене на снимка {}: {}", wooImg.getSrc(), e.getMessage());
                }
//            }
        }
    }

    private void syncAddonsForProduct(WpProductEntity product, List<WooAddonDto> wooAddons, SiteEntity site, LanguageEntity lang) {
        if (wooAddons == null || wooAddons.isEmpty()) return;

        for (WooAddonDto wooAddon : wooAddons) {
            // 1. Намираме или създаваме Глобалния Адон (Групата)
            // Тук търсим по името в преводите
            WpAddonEntity addonGroup = wpAddonRepository.findByNameAndLanguage(wooAddon.getName(), lang)
                    .orElseGet(() -> {
                        WpAddonEntity newAddon = new WpAddonEntity();
                        newAddon.setSlug(SlugTool.generateSlug(wooAddon.getName()));
                        WpAddonEntity savedAddon = wpAddonRepository.save(newAddon);

                        WpAddonTranslationEntity trans = new WpAddonTranslationEntity();
                        trans.setGroup(savedAddon);
                        trans.setLanguage(lang);
                        trans.setName(wooAddon.getName());
                        wpAddonTranslationRepository.save(trans);

                        return savedAddon;
                    });

            // 2. Обработка на опциите (Addon Values)
            for (WooAddonOptionsDto optDto : wooAddon.getOptions()) {
                WpAddonValueEntity valEntity = wpAddonValueRepository.findByLabelAndLanguage(optDto.getLabel(), lang)
                        .orElseGet(() -> {
                            WpAddonValueEntity newVal = new WpAddonValueEntity();
                            newVal.setSlug(SlugTool.generateSlug(optDto.getLabel()));
                            WpAddonValueEntity savedVal = wpAddonValueRepository.save(newVal);

                            WpAddonValueTranslationEntity vTrans = new WpAddonValueTranslationEntity();
                            vTrans.setAddonValue(savedVal);
                            vTrans.setLanguage(lang);
                            vTrans.setLabel(optDto.getLabel());
                            wpAddonValueTranslationRepository.save(vTrans);

                            return savedVal;
                        });

                // Уверяваме се, че стойността (Value) е свързана с групата (Addon)
                // Това поддържа структурата в таблицата wp_addon_addon_values
                if (!addonGroup.getValues().contains(valEntity)) {
                    addonGroup.getValues().add(valEntity);
                    wpAddonRepository.save(addonGroup);
                }

                // 3. СЪЩИНСКАТА ЧАСТ: Конфигурация за конкретния продукт и сайт
                // Проверяваме дали вече имаме такава конфигурация в списъка на продукта
//                WpProductAddonConfigEntity config = product.getAddonConfig().stream()
//                        .filter(c -> c.getSite().getId().equals(site.getId()) &&
//                                c.getAddonValue().getId().equals(valEntity.getId()))
//                        .findFirst()
//                        .orElseGet(() -> {
//                            WpProductAddonConfigEntity newConfig = new WpProductAddonConfigEntity();
//                            newConfig.setProduct(product);
//                            newConfig.setAddonValue(valEntity);
//                            newConfig.setSite(site);
//                            product.getAddonConfig().add(newConfig); // Добавяме към списъка на продукта
//                            return newConfig;
//                        });
                WpProductAddonConfigEntity config = product.getAddonConfig().stream()
                        .filter(c -> c.getAddonValue().getId().equals(valEntity.getId()))
                        .findFirst()
                        .orElseGet(() -> {
                            WpProductAddonConfigEntity newConfig = new WpProductAddonConfigEntity();
                            newConfig.setProduct(product);
                            newConfig.setAddonValue(valEntity);
                            // Вече НЕ сетваме сайт тук!
                            product.getAddonConfig().add(newConfig);
                            return newConfig;
                        });


                config.setPriceModifier(parsePrice(optDto.getPrice()));
                config.setActive(true);

                // Забележка: Понеже имаме CascadeType.ALL в WpProductEntity,
                // не е нужно да викаме ръчно repository.save(config) тук.
                // Тя ще се запише при запазването на целия продукт.
            }
        }
    }


    // Помощна функция за парсване на цена
    private java.math.BigDecimal parsePrice(String price) {
        if (price == null || price.isEmpty()) return java.math.BigDecimal.ZERO;
        try {
            return new java.math.BigDecimal(price);
        } catch (Exception e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private String genSky() {
        // 1. Вземаме продукта с най-високото SKU по азбучен ред
        Optional<WpProductEntity> lastProductOpt = wpProductRepository.findFirstByOrderBySkuDesc();

        if (lastProductOpt.isEmpty()) {
            return "a1000"; // Начално SKU, ако базата е празна
        }

        WpProductEntity lastProduct = lastProductOpt.get();
        String lastSku = lastProduct.getSku();

        try {
            // 2. Използваме Regex, за да разделим буквите от цифрите
            // Намираме само цифрите
            String digits = lastSku.replaceAll("\\D+", "");
            // Намираме префикса (буквите)
            String prefix = lastSku.replaceAll("\\d+", "");

            if (digits.isEmpty()) {
                return lastSku + "1";
            }

            // 3. Парсваме числото и добавяме 1
            long nextNumber = Long.parseLong(digits) + 1;

            // 4. Сглобяваме обратно
            return prefix + nextNumber;

        } catch (Exception e) {
            // Fallback: използваме ID-то само ако форматът на SKU е тотално счупен
            return "a" + (lastProduct.getId() + 10000);
        }
    }
    @Transactional
//    @CacheEvict(value = "productsList", allEntries = true)
    public WpProductDto saveProduct(WpProductDto dto) {
        WpProductEntity entity;
        boolean isHistorical = false;
        if (dto.getId() != null && dto.getId() > 0) {
            entity = wpProductRepository.findById(dto.getId()).orElseThrow();
            isHistorical = true;
        } else {
            entity = new WpProductEntity();
            entity.setSku(genSky());
        }

        if(isHistorical && !Objects.equals(entity.getStockQuantity(), dto.getStockQuantity())) {
            CustomUserDetails user = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            UserEntity userr = userRepository.findByEmail(user.getEmail()).orElseThrow();

            WpProductHistoryEntity historyEntity = new WpProductHistoryEntity();
            historyEntity.setProduct(entity);
            historyEntity.setOldQuantity(Long.valueOf(entity.getStockQuantity()));
            historyEntity.setNewQuantity(Long.valueOf(dto.getStockQuantity()));
            historyEntity.setReason("Changed by User");
            historyEntity.setQuantity(dto.getStockQuantity());
            historyEntity.setUser(userr);
            wpProductHistoryRepository.save(historyEntity);
        }

        entity.setStockQuantity(dto.getStockQuantity());
        entity.setWeight(dto.getWeight());
        entity.setStatus(dto.getStatus());
        entity.setSaleType(dto.getSaleType());

        if (entity.getId() == null) {
            entity = wpProductRepository.saveAndFlush(entity);
        }

        // BRAND
        if (dto.getBrand() != null) {
            wpBrandRepository.findBySlug(dto.getBrand().getSlug()).ifPresent(entity::setBrand);
        }

        // CATEGORY
        if (dto.getCategories() != null && !dto.getCategories().isEmpty()) {
            entity.getCategories().clear();
            for (WpCategoryDetailDto category : dto.getCategories()) {
                entity.getCategories().add(wpCategoryRepository.getReferenceById(category.getId()));
            }
        }

        // ATTRIBUTE VALUES
        if (dto.getAttributeValueIds() != null) {
            entity.getAttributeValues().clear();
            for (Long valueId : dto.getAttributeValueIds()) {
                entity.getAttributeValues().add(wpAttributeValueRepository.getReferenceById(valueId));
            }
        }

        // TRANSLATION
        if (dto.getTranslations() != null) {
            entity.getTranslations().clear();
            for (WpProductTranslationDto tDto : dto.getTranslations()) {
                WpProductTranslationEntity tEntity = new WpProductTranslationEntity();
                tEntity.setName(tDto.getName());
                tEntity.setDescription(tDto.getDescription());
                tEntity.setShortDescription(tDto.getShortDescription());
                tEntity.setProduct(entity);
                if (tDto.getLanguage() != null && tDto.getLanguage().getId() != null) {
                    tEntity.setLanguage(languageRepository.getReferenceById(tDto.getLanguage().getId()));
                }
                entity.getTranslations().add(tEntity);
            }
        }

        // ADDONS
        if (dto.getAddonConfigs() != null) {
            List<WpProductAddonConfigEntity> currentConfigs = entity.getAddonConfig();
            Set<Long> incomingValueIds = dto.getAddonConfigs().stream()
                    .filter(a -> a.getAddonValue() != null)
                    .map(a -> a.getAddonValue().getId())
                    .collect(Collectors.toSet());

            currentConfigs.removeIf(existing -> !incomingValueIds.contains(existing.getAddonValue().getId()));

            for (WpProductAddonConfigDto aDto : dto.getAddonConfigs()) {
                if (aDto.getAddonValue() == null) continue;
                Long valId = aDto.getAddonValue().getId();
                Optional<WpProductAddonConfigEntity> existingOpt = currentConfigs.stream()
                        .filter(e -> e.getAddonValue().getId().equals(valId)).findFirst();

                if (existingOpt.isPresent()) {
                    existingOpt.get().setPriceModifier(aDto.getPriceModifier());
                } else {
                    WpProductAddonConfigEntity newConfig = new WpProductAddonConfigEntity();
                    newConfig.setProduct(entity);
                    newConfig.setAddonValue(wpAddonValueRepository.getReferenceById(valId));
                    newConfig.setPriceModifier(aDto.getPriceModifier());
                    newConfig.setActive(true);
                    currentConfigs.add(newConfig);
                }
            }
        } else {
            entity.getAddonConfig().clear();
        }

        // SITE CONFIG
        if (dto.getSiteConfig() != null && !dto.getSiteConfig().isEmpty()) {

            // 1. Първо намираме sateno.bg от DTO-то
            WpProductSiteConfigDto satenoDto = dto.getSiteConfig().stream()
                    .filter(sc -> sc.getSite() != null && sc.getSite().getUrl().contains("sateno.bg"))
                    .findFirst().orElse(null);

            BigDecimal satenoRegular = satenoDto != null ? satenoDto.getRegularPrice() : BigDecimal.ZERO;
            BigDecimal satenoPrice = satenoDto != null ? satenoDto.getPrice() : BigDecimal.ZERO;

            // 2. Сортираме — sateno.bg първо
            List<WpProductSiteConfigDto> sorted = dto.getSiteConfig().stream()
                    .sorted((a, b) -> {
                        boolean aIsSateno = a.getSite() != null && a.getSite().getUrl().contains("sateno.bg");
                        boolean bIsSateno = b.getSite() != null && b.getSite().getUrl().contains("sateno.bg");
                        return Boolean.compare(!aIsSateno, !bIsSateno); // sateno.bg отива първо
                    })
                    .toList();

            for (WpProductSiteConfigDto wpProductSiteConfigDto : sorted) {
                WpProductSiteConfigEntity siteConfig = wpProductSiteConfigRepository
                        .findById(wpProductSiteConfigDto.getId())
                        .orElse(new WpProductSiteConfigEntity());

//                SiteEntity site = siteRepository.getReferenceById(wpProductSiteConfigDto.getSite().getId());
                Optional<SiteEntity> site = siteRepository.findById(wpProductSiteConfigDto.getSite().getId());
                if (site.isEmpty()){ throw new RuntimeException("site not found"); };
                CurrencyEntity currency = site.get().getCurrency();
                siteConfig.setPrice(wpProductSiteConfigDto.getPrice());
                siteConfig.setRegularPrice(wpProductSiteConfigDto.getRegularPrice());
                siteConfig.setSite(site.get());
                siteConfig.setProduct(entity);

                if (!wpProductSiteConfigDto.getSite().getUrl().contains("sateno.bg")) {
                    if (siteConfig.getPrice() == null || siteConfig.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                        BigDecimal convert = currencyService.convert(satenoPrice, "EUR", currency.getCode().toUpperCase());
                        siteConfig.setPrice(convert);
                    }
                    if (siteConfig.getRegularPrice() == null || siteConfig.getRegularPrice().compareTo(BigDecimal.ZERO) <= 0) {
                        BigDecimal convert = currencyService.convert(satenoRegular, "EUR", currency.getCode().toUpperCase());
                        siteConfig.setRegularPrice(convert);
                    }
                }

                wpProductSiteConfigRepository.save(siteConfig);
            }
        }

        // --- КОРЕКЦИЯ В ЛОГИКАТА ЗА СНИМКИ ---

        // 1. Вземаме ID-тата от Angular
        Set<Long> incomingIds = dto.getImages().stream()
                .map(WpProductImageDto::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        // 2. ИЗТРИВАНЕ НА МАПИНГИ
        if (dto.getLastEditedSiteId() != null) {
            // Локално - само за избрания сайт
            SiteEntity currentSite = siteRepository.getReferenceById(dto.getLastEditedSiteId());
            for (WpProductImageEntity img : entity.getImages()) {
                if (!incomingIds.contains(img.getId())) {
                    wpProductImageSiteMappingRepository.deleteByProductImageIdAndSite(img.getId(), currentSite);
                    log.info("Изтрит локален мапинг за снимка {} от сайт {}", img.getId(), currentSite.getName());
                }
            }
        }
        else {
            // Глобално - за ВСИЧКИ сайтове (няма избран сайт)
            for (WpProductImageEntity img : entity.getImages()) {
                if (!incomingIds.contains(img.getId())) {
                    wpProductImageSiteMappingRepository.deleteAllByProductImageId(img.getId());
                    log.info("Изтрити ВСИЧКИ мапинги за снимка {}", img.getId());
                }
            }
        }

        wpProductImageSiteMappingRepository.flush();

        // 3. ГЛОБАЛНО ИЗТРИВАНЕ: Трием снимката само ако не е в incomingIds И няма мапинги към ДРУГИ сайтове
        List<WpProductImageEntity> imagesToRemoveGlobally = new ArrayList<>();
        for (WpProductImageEntity img : entity.getImages()) {
            if (!incomingIds.contains(img.getId())) {
                // Проверяваме дали след локалното триене горе, са останали други мапинги
                long mappingCount = wpProductImageSiteMappingRepository.countByProductImageId(img.getId());
                if (mappingCount == 0) {
                    imagesToRemoveGlobally.add(img);
                }
            }
        }

        // Физическо триене и премахване от списъка
        for (WpProductImageEntity img : imagesToRemoveGlobally) {
            fileStorageService.deleteProductImage(img.getLocalSrc());
            entity.getImages().remove(img);
        }

        // 4. ДОБАВЯНЕ НА НОВИ (TEMP) СНИМКИ
        if (!dto.getImages().isEmpty()) {
            int currentOrder = 0;

            for (WpProductImageDto imgDto : dto.getImages()) {
                if (imgDto.isTemp()) {
                    String finalPath = fileStorageService.moveTempImageToProductDir(imgDto.getTempName(), entity.getId());
                    if (finalPath != null) {
                        boolean alreadyExists = entity.getImages().stream()
                                .anyMatch(existing -> finalPath.equals(existing.getLocalSrc()));
                        if(!alreadyExists){
                            WpProductImageEntity imageEntity = new WpProductImageEntity();
                            imageEntity.setProduct(entity);
                            imageEntity.setLocalSrc(finalPath);
                            imageEntity.setIsPrimary(imgDto.getIsPrimary() != null &&  imgDto.getIsPrimary());
                            if(imgDto.isVideo()) {
                                WpProductImageEntity imgE = wpProductImageRepository.getReferenceById(imgDto.getParent().getId());
                                imageEntity.setParent(imgE);
                                imageEntity.setVideo(imgDto.isVideo());
                            }
                            wpProductImageRepository.save(imageEntity);
                            entity.getImages().add(imageEntity); // Добавяме към текущата сесия
                        }
                    }
                }
                else {
                    entity.getImages().stream()
                            .filter(img -> img.getId().equals(imgDto.getId()))
                            .findFirst()
                            .ifPresent(img -> {
                                // Актуализираме флага isPrimary (безопасно спрямо null)
                                img.setIsPrimary(Boolean.TRUE.equals(imgDto.getIsPrimary()));
                            });
                }
            }
        }

        // СЪХРАНЯВАМЕ ПРОДУКТА
        entity = wpProductRepository.saveAndFlush(entity);

        // Асинхронна синхронизация
        try {
            wpProductAsyncService.updateProductOnSites(entity, dto.getLastEditedSiteId(), dto.getImages());
        } catch (Exception e) {
            log.error("Async sync error: {}", e.getMessage());
        }

        return modelMapper.map(entity, WpProductDto.class);
    }

    public WooProductDto getProductId(SiteEntity site, Long productId) {
        String auth = AuthTool.getAuth(site.getConsumerKey(), site.getConsumerSecret());

        var response = restClient.get()
                .uri(site.getUrlWithHttps() + PRODUCTS_URL + "/" + productId)
                .header("Authorization", "Basic " + auth)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<WooProductDto>() {});
        return response.getBody();
    }

    @Transactional(readOnly = true)
    public Page<WpProductMinified> getAll(
            Pageable pageable,
            String brand,
            String category,
            Long quantity,
            Long status,
            Long saleType,
            String name_sku
    ) {
        Specification<WpProductEntity> spec = (root, query, cb) -> {
            // 1. МАХАМЕ distinct(true), за да работи orderBy
            query.distinct(false);

            List<Predicate> predicates = new ArrayList<>();

            if (name_sku != null && !name_sku.isEmpty()) {

                String pattern = "%" + name_sku.toLowerCase() + "%";

                // 1. Създаваме Subquery за търсене по ИМЕ в преводите
                Subquery<Long> nameSubquery = query.subquery(Long.class);
                Root<WpProductEntity> subRootName = nameSubquery.from(WpProductEntity.class);
                Join<WpProductEntity, WpProductTranslationEntity> transJoin = subRootName.join("translations");

                nameSubquery.select(subRootName.get("id"))
                        .where(cb.like(cb.lower(transJoin.get("name")), pattern));

                // 2. Дефинираме предикатите за OR условието
                // Проверяваме: (Основно SKU LIKE pattern) ИЛИ (ID-то е в резултатите от имената)
                Predicate skuMatch = cb.like(cb.lower(root.get("sku")), pattern);
                Predicate nameMatch = root.get("id").in(nameSubquery);

                // Добавяме общия OR към списъка с филтри
                predicates.add(cb.or(skuMatch, nameMatch));
            }
            // Филтър по Категория (чрез Subquery)
            if (category != null && !category.isEmpty()) {
                Subquery<Long> catSubquery = query.subquery(Long.class);
                Root<WpProductEntity> subRoot = catSubquery.from(WpProductEntity.class);
                Join<WpProductEntity, WpCategoryEntity> catJoin = subRoot.join("categories");
                Join<WpCategoryEntity, WpCategoryTranslationEntity> transJoin = catJoin.join("translations");

                catSubquery.select(subRoot.get("id"))
                        .where(cb.like(cb.lower(transJoin.get("name")), "%" + category.toLowerCase() + "%"));

                predicates.add(root.get("id").in(catSubquery));
            }

            // Обикновени филтри (директни полета)
            if (brand != null && !brand.isEmpty()) {
                predicates.add(cb.equal(root.get("brand").get("name"), brand));
            }
            if (quantity != null) {
                predicates.add(cb.equal(root.get("stockQuantity"), quantity));
            }
            if (status != null && status >= 0) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if(saleType != null && saleType >= 0) {
                predicates.add(cb.equal(root.get("saleType"), saleType));
            }





            // 2. ВЕЧЕ МОЖЕШ ДА СОРТИРАШ БЕЗОПАСНО
            query.orderBy(
//                    cb.asc(
//                            cb.selectCase()
//                                    .when(cb.equal(root.get("status"), ProductStatus.PUBLISHED), 1)
//                                    .otherwise(2)
//                    ),
//                    cb.asc(root.get("stockQuantity")),
                    cb.desc(root.get("sku"))
            );

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<WpProductEntity> productPage = wpProductRepository.findAll(spec, pageable);
         ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();

        return productPage.map(entity ->
                projectionFactory.createProjection(WpProductMinified.class, entity));
//        );
        // Извикваме репозиторитито с проекцията
//        return wpProductRepository.findBy(spec, pageable);
//        return productPage.map(entity -> modelMapper.map(entity, WpProductDto.class));
    }

    public WpProductDto patchProduct(WpProductDto wpProductDto) {
        Optional<WpProductEntity> byId = wpProductRepository.findById(wpProductDto.getId());
        if (byId.isPresent()) {
            WpProductEntity wpProductEntity = byId.get();
            if (wpProductDto.getSaleType() != null) {
                wpProductEntity.setSaleType(wpProductDto.getSaleType());
            }
            if(wpProductDto.getStockQuantity() != null) {
                wpProductEntity.setStockQuantity(wpProductDto.getStockQuantity());
            }

            WpProductEntity saved = wpProductRepository.save(wpProductEntity);
          try{
              wpProductAsyncService.updateProductOnSites(saved, null);
          } catch (Exception e) {}
            return modelMapper.map(saved, WpProductDto.class);
        }
        throw new RuntimeException("Product not found");
    }

//    @Transactional(Transactional.TxType.REQUIRES_NEW)
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void restoreQuantity(WpOrderEntity wpOrderEntity) {
//        wpOrderEntity = wpOrderRepository.findById(wpOrderEntity.getId()).orElse(null);
//        for (OrderLineItem orderLineItem : wpOrderEntity.getOrderLine()) {
//            String pSku = orderLineItem.getSku();
//
//            Optional<WpProductHistoryEntity> byProductSku = wpProductHistoryRepository.findFirstByProductSkuAndOrder(pSku, wpOrderEntity);
//            if (byProductSku.isPresent()) {
//               WpProductHistoryEntity pHistory = byProductSku.get();
//
//                Optional<WpProductEntity> byId = wpProductRepository.findById(pHistory.getProduct().getId());
//                if (byId.isPresent()) {
//                    WpProductEntity wpProductEntity = byId.get();
//                    wpProductEntity.setStockQuantity(wpProductEntity.getStockQuantity() + pHistory.getQuantity());
//                    wpProductRepository.save(wpProductEntity);
//                    try{
//                        wpProductAsyncService.updateProductOnSites(wpProductEntity, null);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        throw new RuntimeException(e.getMessage());
//                    }
//                }
//
//                wpProductHistoryRepository.delete(pHistory);
//            }
//        }
//    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreQuantity(WpOrderEntity wpOrderEntity) {
        restoreQuantity(wpOrderEntity, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreQuantity(WpOrderEntity wpOrderEntity, boolean manualCancellation) {
        wpOrderEntity = wpOrderRepository.findById(wpOrderEntity.getId()).orElse(null);
        if (wpOrderEntity == null) return;

        for (OrderLineItem orderLineItem : wpOrderEntity.getOrderLine()) {
            String pSku = orderLineItem.getSku();
            int qtyToRestore = orderLineItem.getQuantity();

            if (qtyToRestore <= 0) continue;

            // При автоматичен отказ (куриер) — проверяваме адона за ластик
            // При ръчен отказ — винаги връщаме бройката
            if (!manualCancellation && hasElasticAddon(orderLineItem)) {
                log.info("restoreQuantity: пропускаме {} — продуктът е с ластик (автоматичен отказ)", pSku);
                continue;
            }

            Optional<WpProductEntity> productOpt = wpProductRepository.findBySku(pSku);
            if (productOpt.isPresent()) {
                WpProductEntity wpProductEntity = productOpt.get();

                int oldStock = wpProductEntity.getStockQuantity() != null ? wpProductEntity.getStockQuantity() : 0;
                int newStock = oldStock + qtyToRestore;

                // 1. Обновяваме склада
                wpProductEntity.setStockQuantity(newStock);
                wpProductRepository.save(wpProductEntity);

                // 2. СЪЗДАВАМЕ НОВ ЗАПИС (Вместо да трием стария)
                WpProductHistoryEntity historyEntity = new WpProductHistoryEntity();
                historyEntity.setProduct(wpProductEntity);
                historyEntity.setOrder(wpOrderEntity);
                historyEntity.setOldQuantity((long) oldStock);
                historyEntity.setNewQuantity((long) newStock);
                historyEntity.setQuantity(qtyToRestore);
                historyEntity.setReason("Възстановяване на бройки (Анулирана/Върната поръчка)");

                wpProductHistoryRepository.save(historyEntity);

                // 3. Синхронизация със сайтовете
                try {
                    wpProductAsyncService.updateProductOnSites(wpProductEntity, null);
                } catch (Exception e) {
                    throw new RuntimeException("Грешка при синхронизация: " + e.getMessage());
                }
            }
        }
    }

    private boolean hasElasticAddon(OrderLineItem line) {
        if (line.getPaoIdValue() == null) return false;
        return line.getPaoIdValue().stream()
            .filter(p -> p.getValue() != null)
            .flatMap(p -> p.getValue().stream())
            .anyMatch(v -> {
                String combined = ((v.getValue() != null ? v.getValue() : "") + " "
                        + (v.getRawValue() != null ? v.getRawValue() : "")
                        + " " + (v.getKey() != null ? v.getKey() : "")).toLowerCase();
                return combined.contains("ластик") && !combined.contains("без ластик");
            });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void getFromQuantity(WpOrderEntity wpOrderEntity) {
        // 1. Презареждаме поръчката от базата, за да сме сигурни, че работим с актуалните данни в новата транзакция
        wpOrderEntity = wpOrderRepository.findById(wpOrderEntity.getId()).orElse(null);
        if (wpOrderEntity == null) return;

        // 2. Обхождаме всеки продукт от поръчката
        for (OrderLineItem orderLineItem : wpOrderEntity.getOrderLine()) {
            String pSku = orderLineItem.getSku();
            int qtyToTake = orderLineItem.getQuantity();

            // Пропускаме, ако количеството е 0 или отрицателно
//            if (qtyToTake <= 0) continue;

            // 3. Намираме продукта в склада по неговото SKU
            Optional<WpProductEntity> productOpt = wpProductRepository.findBySku(pSku);
            if (productOpt.isPresent()) {
                WpProductEntity wpProductEntity = productOpt.get();

                int oldStock = wpProductEntity.getStockQuantity() != null ? wpProductEntity.getStockQuantity() : 0;
                int newStock = oldStock - qtyToTake; // Намаляваме наличността в склада

                // Обновяваме продукта в склада
                wpProductEntity.setStockQuantity(newStock);
                wpProductRepository.save(wpProductEntity);

                // 4. Създаваме нов запис в историята на продукта (за да може restoreQuantity да знае какво да върне, ако се наложи)
                WpProductHistoryEntity historyEntity = new WpProductHistoryEntity();
                historyEntity.setProduct(wpProductEntity);
                historyEntity.setOrder(wpOrderEntity); // Свързваме историята с конкретната поръчка
//                historyEntity.setProductSku(pSku);
                historyEntity.setOldQuantity((long) oldStock);
                historyEntity.setNewQuantity((long) newStock);
                historyEntity.setQuantity(qtyToTake);
                historyEntity.setReason("Вземане на бройки при пускане/възобновяване на поръчка");

                // Ако имаш текущ потребител в сесията, можеш да го сетнеш тук (по желание)
                // historyEntity.setChangerName("System/User");

                wpProductHistoryRepository.save(historyEntity);

                // 5. Синхронизираме промяната в склада асинхронно с WooCommerce сайтовете
                try {
                    wpProductAsyncService.updateProductOnSites(wpProductEntity, null);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("Грешка при синхронизация на продукта: " + e.getMessage());
                }
            }
        }
    }


//    @Transactional
//    public void syncProductsToSite(Long siteId) {
//        SiteEntity site = siteRepository.findById(siteId).orElseThrow();
//        if(site.getUrl().contains("sateno.bg")) {
//            throw new RuntimeException("sateno.bg");
//        }
//
//        try { clearAllProductsFromSite(site); } catch (Exception e) { log.error("Грешка при чистене на продукти: {}", e.getMessage()); }
//        try { clearAllCategoriesFromSite(site); } catch (Exception e) { log.error("Грешка при чистене на категории: {}", e.getMessage()); }
//        try { clearAllBrandsFromSite(site); } catch (Exception e) { log.error("Грешка при чистене на брандове: {}", e.getMessage()); }
//
//        CompletableFuture<Boolean> categoryCompletableFuture = wpCategoryAsyncService.syncWpCategoryToSite(siteId);
//        CompletableFuture<Boolean> brandCompletableFuture = wpBrandAsyncService.syncAllBrandsToSite(siteId);
//
//        CompletableFuture.allOf(categoryCompletableFuture, brandCompletableFuture).join();
//
//
//        AtomicInteger count = new AtomicInteger(1);
//        List<WpProductEntity> allWithAddons = wpProductRepository.findAll();
//        ExecutorService executor = Executors.newFixedThreadPool(10);
//
//
//        for (WpProductEntity wpProductEntity : allWithAddons) {
//            executor.submit(() -> {
//                try {
////                massSyncAllToSite(wpProductEntity, site);
////                syncSalePriceBySku(site, wpProductEntity);
//                    translateProductInfos(wpProductEntity, site);
//                    count.getAndIncrement();
////                    System.out.println(count.get());
//                }
//                catch (Exception e) {
//                    // Ако един продукт гръмне, записваме грешката и преминаваме на следващия
//                    log.error("КРИТИЧНА ГРЕШКА за продукт SKU {}: {}", wpProductEntity.getSku(), e.getMessage());
//                }
//
//            });
//
//        }
//        executor.shutdown();
//
//        try {
//            // Чакаме нишките да приключат. Сложи достатъчно време (напр. 1 час)
//            if (!executor.awaitTermination(10, TimeUnit.HOURS)) {
//                executor.shutdownNow(); // Ако не приключат за 1 час, ги спри принудително
//            }
//        } catch (InterruptedException e) {
//            executor.shutdownNow();
//            Thread.currentThread().interrupt();
//        }
//        log.info("Синхронизацията приключи. Успешно обработени: {} продукта.брой {}", count.get(), allWithAddons.size());
//
//
//    }

    private void translateProductInfos(WpProductEntity product, SiteEntity site) {
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
        Integer wpId = (Integer) searchResponse.get(0).get("id");
        String description = (String) searchResponse.get(0).get("description");
        String shortDescription = (String) searchResponse.get(0).get("short_description");
        String name = (String) searchResponse.get(0).get("name");
        List<Map<String, Object>> addons = (List<Map<String, Object>>) searchResponse.get(0).get("addons");

        String targetLang = "полски"; // Може да е site.getLanguage().getName()
        String instruction = "Преведи на " + targetLang + ". Запази всички HTML тагове, емотикони и форматиране.";
        String instructionJson = """
    Ти си JSON преводач за WooCommerce. 
    Твоята задача е да преведеш съдържанието на предоставения JSON на %s език.
    1. Преведи стойностите срещу ключовете "name" и "label".
    2. НЕ променяй нищо друго в структурата на JSON-а.
    3. Запази всички числа и размери (напр. 180 х 240 см).
    4. Върни САМО валиден JSON код, без никакви обяснения.
    """.formatted(targetLang); // Тук вмъкваме езика динамично


        String translatedName = chatGptService.translateText(name, instruction);
        String translatedDesc = chatGptService.translateText(description, instruction);
        String translatedShortDesc = chatGptService.translateText(shortDescription, instruction);

        // 3. ОБНОВЯВАНЕ в WordPress
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("name", translatedName);
        updateBody.put("description", translatedDesc);
        updateBody.put("short_description", translatedShortDesc);
        if (addons != null && !addons.isEmpty()) {
            log.info("Намерени са {} адона за превод.", addons.size());

            ObjectMapper mapper = new ObjectMapper();
            try {
                // 1. Превръщаме списъка в JSON String
                String originalAddonsJson = mapper.writeValueAsString(addons);
                String translatedAddonsJson = chatGptService.translateText(originalAddonsJson, instructionJson);
                // 3. Превръщаме преведения JSON обратно в Java списък
                List<Map<String, Object>> translatedAddons = mapper.readValue(
                        translatedAddonsJson,
                        new TypeReference<List<Map<String, Object>>>() {}
                );

                // 4. Добавяме ги към тялото за обновяване под същия ключ
                updateBody.put("addons", translatedAddons);

            } catch (Exception e) {
                log.error("Грешка при превода на директните адони: {}", e.getMessage());
            }
        }

//        System.out.println(updateBody);

        try {
            restClient.put()
                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/" + wpId)
                    .header("Authorization", "Basic " + auth)
                    .body(updateBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("✅ Успешно преведен и обновен продукт SKU: {} в сайта {}", product.getSku(), site.getUrl());
        } catch (Exception e) {
            log.error("❌ Грешка при запис на превода за SKU {}: {}", product.getSku(), e.getMessage());
        }
    }

    private void syncSalePriceBySku(SiteEntity site, WpProductEntity product) {
        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

        try {

            WpProductSiteConfigEntity satenoConfig = wpProductSiteConfigRepository
                    .findBySiteUrlAndProduct("sateno.bg", product);

            // СТЪПКА 1: Намираме продукта в сайта по SKU, за да му вземем ID-то
            var searchResponse = restClient.get()
                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products?sku=" + product.getSku())
                    .header("Authorization", "Basic " + auth)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (searchResponse == null || searchResponse.isEmpty()) {
                log.warn("Продукт с SKU {} не е намерен в сайта {}", product.getSku(), site.getUrl());
                return;
            }

            // Взимаме WordPress ID-то от първия намерен резултат
            Integer wpId = (Integer) searchResponse.get(0).get("id");


            // СТЪПКА 3 & 4: Подготвяме PATCH заявката за обновяване
            Map<String, Object> updateBody = new HashMap<>();
            String salePriceValue = (satenoConfig.getSalePrice() != null && satenoConfig.getSalePrice().compareTo(BigDecimal.ZERO) > 0)
                    ? satenoConfig.getSalePrice().toString()
                    : ""; // Изпращаме празен низ, за да премахнем промоцията
            updateBody.put("manage_stock", product.getSaleType() != ProductSaleType.UNLIMITED);
            updateBody.put("stock_quantity", product.getStockQuantity());


//            updateBody.put("sale_price", salePriceValue);
//            updateBody.put("featured", false);

            // 1. Взимаме конфигурациите от Sateno
//            SiteEntity satenoSite = siteRepository.findSiteEntityByUrl("sateno.bg");
//            for (WpProductAddonConfigEntity wpProductAddonConfigEntity : product.getAddonConfig()) {
//                WpAddonValueEntity addonValue = wpProductAddonConfigEntity.getAddonValue();
//
//                List<WpAddonEntity> groups = addonValue.getGroups();
//                for (WpAddonEntity group : groups) {
//                    List<WpAddonTranslationEntity> translations1 = group.getTranslations();
//                    for (WpAddonTranslationEntity wpAddonTranslationEntity : translations1) {
//                        System.out.println(wpAddonTranslationEntity.getName());
//                    }
//                }
//
//                List<WpAddonValueTranslationEntity> translations = addonValue.getTranslations();
//                for (WpAddonValueTranslationEntity wpAddonValueTranslationEntity : translations) {
//                    System.out.println(wpAddonValueTranslationEntity.getLabel());
//                }
//            }


            // 2. ГРУПИРАНЕ НА АДОНИТЕ (със защита против дублиране по име)
            // Ключ: Името на групата (чисто), Стойност: Картата на адона за WP
//            Map<String, Map<String, Object>> uniqueAddonsByName = new LinkedHashMap<>();
//
//            for (WpProductAddonConfigEntity config : product.getAddonConfig()) {
//                WpAddonValueEntity addonValue = config.getAddonValue();
//                List<WpAddonEntity> addonGroups = wpAddonRepository.findAllByValuesContaining(addonValue);
//
//                for (WpAddonEntity group : addonGroups) {
//                    // Взимаме името на ГРУПАТА за текущия език
//                    String groupName = group.getTranslations().stream()
//                            .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
//                            .map(WpAddonTranslationEntity::getName)
//                            .findFirst()
//                            .orElse("Опция");
//
//                    // Вместо split(",")[0], използвай по-интелигентно рязане
//                    String cleanGroupName = groupName;
//                    if (groupName.contains("модел")) {
//                        cleanGroupName = groupName.split("модел")[0].trim();
//                        // Премахваме запетаята, ако е останала накрая
//                        if (cleanGroupName.endsWith(",")) {
//                            cleanGroupName = cleanGroupName.substring(0, cleanGroupName.length() - 1).trim();
//                        }
//                    }
//                    // Ако тази група още не съществува в мапа, създаваме я
//                    uniqueAddonsByName.computeIfAbsent(cleanGroupName, name -> {
//                        Map<String, Object> newGroup = new HashMap<>();
//                        newGroup.put("name", name);
//                        newGroup.put("type", "multiple_choice");
//                        newGroup.put("display", "radiobutton");
//                        newGroup.put("required", true);
//                        newGroup.put("position", uniqueAddonsByName.size());
//                        newGroup.put("title_format", "label");
//                        newGroup.put("adjust_price", false);
//                        newGroup.put("options", new ArrayList<Map<String, Object>>());
//                        return newGroup;
//                    });
//
//                    // 3. Подготвяме опцията
//                    Map<String, Object> option = new HashMap<>();
//                    String label = addonValue.getTranslations().stream()
//                            .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
//                            .map(WpAddonValueTranslationEntity::getLabel)
//                            .findFirst()
//                            .orElse("Стойност");
//
//                    option.put("label", label);
//                    option.put("price", (config.getPriceModifier() != null && config.getPriceModifier().compareTo(BigDecimal.ZERO) > 0)
//                            ? config.getPriceModifier().toString() : "");
//                    option.put("price_type", "flat_fee");
//                    option.put("image", 0);
//                    option.put("visibility", true);
//
//                    // Добавяме опцията към правилната група (без да дублираме самата група)
//                    List<Map<String, Object>> optionsList = (List<Map<String, Object>>) uniqueAddonsByName.get(cleanGroupName).get("options");
//
//                    // Опционално: Проверка за дублирана опция вътре в групата (по label)
//                    boolean alreadyExists = optionsList.stream().anyMatch(o -> o.get("label").equals(label));
//                    if (!alreadyExists) {
//                        optionsList.add(option);
//                    }
//                }
//            }
//
//            // 4. Превръщаме мапа обратно в списък за PATCH тялото
//            List<Map<String, Object>> finalAddons = new ArrayList<>(uniqueAddonsByName.values());
//
//            if (!finalAddons.isEmpty()) {
//                updateBody.put("addons", finalAddons);
//            }


//            System.out.println(updateBody.toString());
            restClient.patch()
                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/" + wpId)
                    .header("Authorization", "Basic " + auth)
                    .body(updateBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Успешно обновен sale_price за SKU {}: {}", product.getSku(), salePriceValue);

        } catch (Exception e) {
            log.error("Грешка при синхронизация на цена за SKU {}: {}", product.getSku(), e.getMessage());
        }
    }

//    private void massSyncAllToSite(WpProductEntity product, SiteEntity site) {
//
//
//
//        WpProductSiteConfigEntity siteConfig = wpProductSiteConfigRepository
//                .findByProductAndSite(product, site)
//                .orElse(new WpProductSiteConfigEntity());
//
//        WpProductSiteConfigEntity satenoConfig = wpProductSiteConfigRepository
//                .findBySiteUrlAndProduct("sateno.bg", product);
//
//        WpProductTranslationEntity translation = wpProductTranslationRepository
//                .findByProductAndLanguage(product, site.getLanguage())
//                .orElseThrow(() -> new RuntimeException("Липсва превод за този сайт"));
//
//        // --- ЛОГИКА ЗА СНИМКИ ---
//        List<Map<String, Object>> imageList = new ArrayList<>();
//        if (product.getImages() != null) {
//            for (WpProductImageEntity imgEntity : product.getImages()) {
//                // Викаме твоя метод за качване
//                Long wpMediaId = imageToWordPress.uploadImageToWordPress(site, imgEntity.getLocalSrc());
//                if (wpMediaId != null) {
//                    Map<String, Object> imgMap = new HashMap<>();
//                    imgMap.put("id", wpMediaId); // Свързваме чрез ID в Media Library
//                    imageList.add(imgMap);
//                }
//            }
//        }
//
//        // Подготвяме тялото на заявката
//        Map<String, Object> body = new HashMap<>();
//        body.put("sku", product.getSku());
//        body.put("name", translation.getName());
//        body.put("type", product.getType());
//        body.put("status", product.getStatus().getValue());
//        body.put("description", translation.getDescription());
//        body.put("short_description", translation.getShortDescription());
////        body.put("regular_price", siteConfig.getRegularPrice() != null ? siteConfig.getRegularPrice().toString() : "0");
//        body.put("regular_price", satenoConfig.getRegularPrice() != null ? satenoConfig.getRegularPrice().toString() : "0");
//        body.put("price", satenoConfig.getRegularPrice() != null ? satenoConfig.getRegularPrice().toString() : "0");
//        body.put("manage_stock", product.isManage_stock());
//        body.put("catalog_visibility", product.getCatalog_visibility());
//        body.put("stock_quantity", product.isManage_stock()? product.getStockQuantity(): null);
//        body.put("featured", product.isFeatured());
//        body.put("images", imageList); // ДОБАВЯМЕ СНИМКИТЕ ТУК
//        body.put("sale_price", satenoConfig.getSalePrice() != null? satenoConfig.getSalePrice().toString() : "0");
////        if(product.getSaleType() == ProductSaleType.UNLIMITED){
//            body.put("stock_status",product.getStock_status());
////        }
//
//
//
//        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
//
//
//        List<Map<String, Object>> categoryList = uploadCategoriesToWordpress(site, product, auth);
//        body.put("categories", categoryList);
//
//        List<Map<String, Object>> brandList = uploadBrandsToWordpress(site, product, auth);
//        body.put("brands", brandList);
//
//        try {
//            var response = restClient.post()
//                    .uri(site.getUrlWithHttps() + PRODUCTS_URL)
//                    .header("Authorization", "Basic " + auth)
//                    .body(body)
//                    .retrieve()
//                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
//
//            if (response != null && response.containsKey("id")) {
//                Integer wpId = (Integer) response.get("id");
//                siteConfig.setProduct(product);
//                siteConfig.setSite(site);
//                siteConfig.setWpProductId(Long.valueOf(wpId));
//                wpProductSiteConfigRepository.save(siteConfig);
//                log.info("Успешно създаден продукт в WP с ID: {} и прикачени {} снимки", wpId, imageList.size());
//            }
//
//        } catch (Exception e) {
//            log.error("Грешка при POST към WooCommerce: {}", e.getMessage());
//        }
//    }

//    public Long uploadImageToWordPress(SiteEntity site, String localPath) {
//        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
//
//        // Взимаме байтовете от локалния диск
//        byte[] imageBytes = fileStorageService.getImageBytes(localPath);
//        String fileName = localPath.substring(localPath.lastIndexOf("/") + 1);
//
//        try {
//            var response = restClient.post()
//                    .uri(site.getUrlWithHttps() + "/wp-json/wp/v2/media")
//                    .header("Authorization", "Basic " + auth)
//                    .header("Content-Disposition", "attachment; filename=" + fileName)
//                    .header("Content-Type", "image/jpeg") // или динамично според разширението
//                    .body(imageBytes)
//                    .retrieve()
//                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
//
//            // Връща ID-то на новосъздадената медия в WordPress
//            return Long.valueOf(response.get("id").toString());
//        } catch (Exception e) {
//            log.error("Грешка при качване на снимка в WP: {}", e.getMessage());
//            return null;
//        }
//    }

    private List<Map<String, Object>> uploadCategoriesToWordpress(SiteEntity site, WpProductEntity product, String auth) {
        List<Map<String, Object>> productCategories = new ArrayList<>();
        for (WpCategoryEntity category : product.getCategories()) {

            try {

                var searchResponse = restClient.get()
                        .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories?slug=" + category.getSlug())
                        .header("Authorization", "Basic " + auth)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                Long wpCategoryId;

                if (searchResponse != null && !searchResponse.isEmpty()) {
                    // Категорията съществува
                    wpCategoryId = Long.valueOf(searchResponse.get(0).get("id").toString());
                    log.info("Намерена съществуваща категория: {} с ID: {}", category.getSlug(), wpCategoryId);
                } else {
                    // 2. Категорията липсва - СЪЗДАВАМЕ Я (POST)
                    // Взимаме превода за езика на сайта
                    String catName = category.getTranslations().stream()
                            .filter(t -> t.getLanguage().getId().equals(site.getLanguage().getId()))
                            .map(WpCategoryTranslationEntity::getName)
                            .findFirst()
                            .orElse(category.getSlug()); // Fallback към slug ако няма превод

                    Map<String, Object> body = new HashMap<>();
                    body.put("name", catName);
                    body.put("slug", category.getSlug());

                    var createResponse = restClient.post()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories")
                            .header("Authorization", "Basic " + auth)
                            .body(body)
                            .retrieve()
                            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

                    wpCategoryId = Long.valueOf(createResponse.get("id").toString());
                    log.info("Създадена нова категория: {} с ID: {}", category.getSlug(), wpCategoryId);
                }

                Map<String, Object> catMap = new HashMap<>();
                catMap.put("id", wpCategoryId);
                productCategories.add(catMap);

            } catch (Exception e) {
                log.error("Грешка при търсене на категория по slug '{}': {}", category.getSlug(), e.getMessage());
            }
        }

        return productCategories;


    }

    private List<Map<String, Object>> uploadBrandsToWordpress(SiteEntity site, WpProductEntity product, String auth) {
        List<Map<String, Object>> productBrands = new ArrayList<>();

        if (product.getBrand() == null) {
            return productBrands;
        }

        WpBrandEntity brand = product.getBrand();

        try {
            // 1. Търсим дали брандът съществува по Slug
            // Забележка: Пътят може да варира според плъгина (пробвай този първо)
            var searchResponse = restClient.get()
                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/brands?slug=" + brand.getSlug())
                    .header("Authorization", "Basic " + auth)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            Long wpBrandId;

            if (searchResponse != null && !searchResponse.isEmpty()) {
                // Вече съществува
                wpBrandId = Long.valueOf(searchResponse.get(0).get("id").toString());
                log.info("Намерен съществуващ бранд: {} с ID: {}", brand.getSlug(), wpBrandId);
            } else {
                // 2. Липсва - създаваме го
                Map<String, Object> body = new HashMap<>();
                body.put("name", brand.getName());
                body.put("slug", brand.getSlug());

                var createResponse = restClient.post()
                        .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/brands")
                        .header("Authorization", "Basic " + auth)
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});

                wpBrandId = Long.valueOf(createResponse.get("id").toString());
                log.info("Създаден нов бранд: {} с ID: {}", brand.getSlug(), wpBrandId);
            }

            // Подготвяме мапа за масива "brands" в обекта на продукта
            Map<String, Object> brandMap = new HashMap<>();
            brandMap.put("id", wpBrandId);
            productBrands.add(brandMap);

        } catch (Exception e) {
            log.error("Грешка при синхронизация на бранд '{}': {}", brand.getSlug(), e.getMessage());
        }

        return productBrands;
    }

//    @Transactional
//    protected void clearAllProductsFromSite(SiteEntity site) {
//        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
//
//        log.info("Започва изчистване на всички продукти от сайт: {}", site.getUrl());
//
//        while (true) {
//            // 1. Четем само ID-тата на първите 100 продукта (pageable не ни трябва, защото трием първите и следващите 100 стават първи)
//            var response = restClient.get()
//                    .uri(site.getUrlWithHttps() + PRODUCTS_URL + "?per_page=100&_fields=id")
//                    .header("Authorization", "Basic " + auth)
//                    .retrieve()
//                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
//
//            if (response == null || response.isEmpty()) {
//                log.info("Няма повече продукти за изтриване.");
//                break;
//            }
//
//            // Извличаме само ID-тата
//            List<Long> idsToDelete = response.stream()
//                    .map(m -> Long.valueOf(m.get("id").toString()))
//                    .toList();
//
//            // 2. Изтриваме ги чрез Batch API (force=true за окончателно изтриване без кошче)
//            Map<String, Object> deleteBody = new HashMap<>();
//            deleteBody.put("delete", idsToDelete);
//
//            try {
//                restClient.post()
//                        .uri(site.getUrlWithHttps() + PRODUCTS_URL + "/batch")
//                        .header("Authorization", "Basic " + auth)
//                        .body(deleteBody)
//                        .retrieve()
//                        .toBodilessEntity();
//
//                log.info("Успешно изтрити {} продукта (Batch).", idsToDelete.size());
//            } catch (Exception e) {
//                log.error("Грешка при batch изтриване: {}", e.getMessage());
//                break; // Спираме при грешка, за да не влезем в безкраен цикъл
//            }
//        }
//
//        // 3. СЛЕД КАТО СМЕ ИЗТРИЛИ ВСИЧКО В WP:
//        // Трябва да занулим wpProductId в нашата база, за да знае системата, че следващия път прави POST
////        wpProductSiteConfigRepository.clearWpIdsForSite(site.getId());
//    }

//@Transactional(propagation = Propagation.REQUIRES_NEW)
//protected void clearAllProductsFromSite(SiteEntity site) {
//    String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
//    log.info("Започва пълно изчистване на продукти и медия от сайт: {}", site.getUrl());
//
//    while (true) {
//        // 1. Взимаме продуктите ЗАЕДНО със снимките им
//        var response = restClient.get()
//                .uri(site.getUrlWithHttps() + PRODUCTS_URL + "?per_page=100&_fields=id,images")
//                .header("Authorization", "Basic " + auth)
//                .retrieve()
//                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
//
//        if (response == null || response.isEmpty()) {
//            log.info("Няма повече продукти за изтриване.");
//            break;
//        }
//
//        List<Long> productIds = new ArrayList<>();
//        Set<Long> mediaIdsToDelete = new HashSet<>();
//
//        for (Map<String, Object> product : response) {
//            productIds.add(Long.valueOf(product.get("id").toString()));
//
//            // Извличаме ID-тата на снимките на този продукт
//            List<Map<String, Object>> images = (List<Map<String, Object>>) product.get("images");
//            if (images != null) {
//                for (Map<String, Object> img : images) {
//                    mediaIdsToDelete.add(Long.valueOf(img.get("id").toString()));
//                }
//            }
//        }
//
//        // 2. ИЗТРИВАМЕ ПРОДУКТИТЕ (Batch)
//        deleteProductsBatch(site, productIds, auth);
//
//        // 3. ИЗТРИВАМЕ СНИМКИТЕ (Една по една, защото WP Media API няма Batch Delete по подразбиране)
////        deleteMediaOneByOne(site, mediaIdsToDelete, auth);
//    }
//
//    try {
//        wpProductImageSiteMappingRepository.deleteAllBySite(site);
//        log.info("Успешно изтрити локалните мапинги на СНИМКИТЕ за сайт: {}", site.getUrl());
//    } catch (Exception e) {
//        log.error("Грешка при триене на мапингите за снимки за сайт {}: {}", site.getUrl(), e.getMessage());
//    }
//}

//    private void deleteProductsBatch(SiteEntity site, List<Long> ids, String auth) {
//        Map<String, Object> deleteBody = Map.of("delete", ids);
//        try {
//            restClient.post()
//                    .uri(site.getUrlWithHttps() + PRODUCTS_URL + "/batch")
//                    .header("Authorization", "Basic " + auth)
//                    .body(deleteBody)
//                    .retrieve()
//                    .toBodilessEntity();
//            log.info("Успешно изтрити {} продукта.", ids.size());
//        } catch (Exception e) {
//            log.error("Грешка при batch изтриване на продукти: {}", e.getMessage());
//        }
//    }

//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    protected void clearAllCategoriesFromSite(SiteEntity site) {
//        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
//        log.info("Започва изчистване на категории от сайт: {}", site.getUrl());
//
//        while (true) {
//            // Взимаме първите 100 категории
//            var response = restClient.get()
//                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories?per_page=100&_fields=id")
//                    .header("Authorization", "Basic " + auth)
//                    .retrieve()
//                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
//
//            // Филтрираме ID 18 (обикновено Uncategorized), защото не може да се трие
//            List<Long> idsToDelete = response.stream()
//                    .map(m -> Long.valueOf(m.get("id").toString()))
////                    .filter(id -> id != 18) // Замени 18 с ID-то на твоята default категория ако е различно
//                    .toList();
//
//            if (idsToDelete.isEmpty() || idsToDelete.size() == 1) break;
//
//            Map<String, Object> deleteBody = Map.of("delete", idsToDelete);
//
//            restClient.post()
//                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories/batch")
//                    .header("Authorization", "Basic " + auth)
//                    .body(deleteBody)
//                    .retrieve()
//                    .toBodilessEntity();
//
//            log.info("Изтрити {} категории.", idsToDelete.size());
//        }
//
//        try {
//            wpCategorySiteMappingRepository.deleteAllBySite(site);
//            log.info("Успешно изтрити локалните мапинги на категориите за сайт: {}", site.getUrl());
//        } catch (Exception e) {
//            log.error("Грешка при триене на локалните мапинги за сайт {}: {}", site.getUrl(), e.getMessage());
//        }
//    }

//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    protected void clearAllBrandsFromSite(SiteEntity site) {
//        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
//        log.info("Започва изчистване на брандове от сайт: {}", site.getUrl());
//
//        while (true) {
//            // Внимание: Endpoint-ът трябва да съвпада с този, който ползваш за качване
//            var response = restClient.get()
//                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/brands?per_page=100&_fields=id")
//                    .header("Authorization", "Basic " + auth)
//                    .retrieve()
//                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
//
//            if (response == null || response.isEmpty()) break;
//
//            List<Long> idsToDelete = response.stream()
//                    .map(m -> Long.valueOf(m.get("id").toString()))
//                    .toList();
//
//            Map<String, Object> deleteBody = Map.of("delete", idsToDelete);
//
//            restClient.post()
//                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/brands/batch")
//                    .header("Authorization", "Basic " + auth)
//                    .body(deleteBody)
//                    .retrieve()
//                    .toBodilessEntity();
//
//            log.info("Изтрити {} бранда.", idsToDelete.size());
//        }
//    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSingleTranslation(Long productId, Long langId, WpProductTranslationDto dto, Long type) {
        WpProductEntity product = wpProductRepository.getReferenceById(productId);
        LanguageEntity lang = languageRepository.getReferenceById(langId);

        WpProductTranslationEntity translation = wpProductTranslationRepository
                .findByProductAndLanguage(product, lang)
                .orElseGet(() -> {
                    WpProductTranslationEntity newEntity = new WpProductTranslationEntity();
                    newEntity.setProduct(product);
                    newEntity.setLanguage(lang);
                    return newEntity;
                });

        if (type == 1L) translation.setName(dto.getName());
        else if (type == 2L) translation.setShortDescription(dto.getShortDescription());
        else if (type == 3L) translation.setDescription(dto.getDescription());

        wpProductTranslationRepository.saveAndFlush(translation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveTranslatedField(Long productId, Long langId, String text, Long type) {
        WpProductEntity product = wpProductRepository.getReferenceById(productId);
        LanguageEntity lang = languageRepository.getReferenceById(langId);

        WpProductTranslationEntity translation = wpProductTranslationRepository
                .findByProductAndLanguage(product, lang)
                .orElseGet(() -> {
                    WpProductTranslationEntity newEntity = new WpProductTranslationEntity();
                    newEntity.setProduct(product);
                    newEntity.setLanguage(lang);
                    return newEntity;
                });

        if (type == 1L) translation.setName(text);
        else if (type == 2L) translation.setShortDescription(text);
        else if (type == 3L) translation.setDescription(text);

        wpProductTranslationRepository.saveAndFlush(translation);
    }


    public AIProductGenDTO  aiProductGen(AIProductGenDTO dto) {

        SchemeWpProductEntity scheme = schemeWpProductRepository.findById(dto.getSchemeId())
                .orElseThrow(() -> new RuntimeException("Scheme not found"));

        List<String> imagePaths = dto.getTempImages().stream()
                .map(img -> {

                    if(img.getLocalSrc() != null) {
                        return fileStorageService.getFullPhysicalFilePath(img.getLocalSrc());
                    }
                   return fileStorageService.getFullTempFilePath(img.getTempName());
                })
                .filter(Objects::nonNull)
                .toList();

        String target = switch (dto.getStep().intValue()) {
            case 1 -> "КРАТКО ИМЕ НА ПРОДУКТ";
//            case 2 -> "КРАТКО ОПИСАНИЕ";
            case 2 -> "ПЪЛНО ОПИСАНИЕ";
            default -> "ТЕКСТ";
        };

//        String brandName = (dto.getProductInfo().getBrand() != null)
//                ? dto.getProductInfo().getBrand().getName()
//                : "Не е посочена";

        String productContext =
                "Данни за продукта:\n" +
//                        "- Марка: " + brandName + "\n" +
                        "- Категория: " + String.join(", ",
                        dto.getProductInfo()
                                .getCategories()
                                .stream()
                                .map(WpCategoryDetailDto::getName)
                                .toList()
                ) + "\n" +
                        "- Тегло: " + dto.getProductInfo().getWeight() + "\n";

        String instructions = switch (dto.getStep().intValue()) {
            case 1 -> scheme.getTitle();
//            case 2 -> scheme.getShortDescription();
            case 2 -> scheme.getDescription();
            default -> "";
        };


        String refinementBlock = "";

        if (dto.getRefinement() != null && !dto.getRefinement().isBlank()) {
            refinementBlock =
                    "\n🚨 OVERRIDE INSTRUCTIONS (highest priority):\n" +
                            dto.getRefinement() +
                            "\n";
        }

        String rr = dto.getPreviousTexts().entrySet().stream()
                .map(entry -> {
                    String type = switch (entry.getKey().intValue()) {
                        case 1 -> "Име на продукт: ";
//                        case 2 -> "Кратко описание: ";
                        case 2 -> "Описание: ";
                        default -> " ";
                    };
                    return type + entry.getValue();
                })
                .collect(Collectors.joining("\n---\n"));
        // Използваме joining с нов ред и разделител за по-добра яснота за AI

        String prompt =
                        productContext +
                        "\nЗАДАЧА: " + target + "\n\n" +
                        "ИНСТРУКЦИЯ ОТ ШАБЛОНА:\n" +
                        instructions +
                              "ИМАЙ ГО В ПРЕДВИД ТИЯ -"  +
                                rr +
                                refinementBlock ;

        String aiResponse = chatGptService.generateWithImages(prompt, imagePaths);

        dto.setResponseAI(aiResponse);
        return dto;
    }

    @Transactional
    public boolean deleteProducts(Long[] ids) {
        for (Long id : ids) {
            WpProductEntity product = wpProductRepository.findById(id).orElse(null);
            if (product == null) continue;

            if (product.getImages() != null) {
                for (WpProductImageEntity img : product.getImages()) {
                    fileStorageService.deleteProductImage(img.getLocalSrc());
                }
            }

            String pSku = product.getSku();
            wpProductRepository.delete(product);

        wpProductAsyncService.deleteProductFromSites(pSku);


        }

        return true;
    }


}
