package com.sateno_b.www.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.enums.ProductSaleType;
import com.sateno_b.www.model.enums.ProductStatus;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.shared.AuthTool;
import com.sateno_b.www.shared.SlugTool;
import com.sateno_b.www.shared.ImageToWordPress;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.*;
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


    @Transactional
    @CacheEvict(value = "productsList", allEntries = true)
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
                    wpProductTranslationRepository.flush();
                    wpProductSiteConfigRepository.flush();
                    wpProductImageSiteMappingRepository.flush();
                    wpAddonRepository.flush();
                    entityManager.clear();
                }

            } catch (Exception e) {
                log.error("грешка при обработка на продукт SKU: {}, грешка: {}", product.getSku(), e.getMessage());
            }
        }

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
            return;
        }

        WpProductEntity product = new WpProductEntity();
        // 2. Обновяваме Глобалните данни (технически)
        product.setStockQuantity(dto.getStock_quantity() == null? 0: dto.getStock_quantity());
        product.setWeight(dto.getWeight());
        product.setStatus(dto.getStatus());
        product.setSaleType(dto.isManage_stock() ? ProductSaleType.LIMITED: ProductSaleType.UNLIMITED);
        product.setManage_stock(dto.isManage_stock());
        product.setSku(dto.getSku());
        product.setStock_status(dto.getStock_status());
        product.setType(dto.getType());
        product.setFeatured(dto.isFeatured());
        product.setCatalog_visibility(dto.getCatalog_visibility());
        product.setDimensions(dto.getDimensions());
        // 3. Свързваме Бранд (вече синхронизиран)
        if (dto.getBrands() != null && !dto.getBrands().isEmpty()) {
            wpBrandRepository.findBySlug(SlugTool.decodeSlug(dto.getBrands().get(0).getSlug()))
                    .ifPresent(product::setBrand);
        }

        // 4. Свързваме Категории и Подкатегории (ManyToMany)
        if (dto.getCategories() != null) {
            product.getCategories().clear(); // Махаме старите, слагаме новите от WP
            for (WooProductCategoryDto catDto : dto.getCategories()) {
                wpCategoryRepository.findBySlug(SlugTool.decodeSlug(catDto.getSlug()))
                        .ifPresent(product.getCategories()::add);
            }
        }

        product = wpProductRepository.save(product);

        // 5. ЗАПИС НА ЦЕНИ И ТЕКСТОВЕ (Translation)
        updateTranslation(product, dto, site, lang);

        // 5a запис на цени
        updateSiteConfig(product, dto, site);

        // 6. СНИМКИ (Локално сваляне)
        syncImagesForProduct(product, dto.getImages(), site);

        // 7. АДОНИ (Специфични за продукта и сайта)
        syncAddonsForProduct(product, dto.getAddons(), site, lang);

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
    @CacheEvict(value = "productsList", allEntries = true)
    public WpProductDto saveProduct(WpProductDto dto) {
        WpProductEntity entity;
        if (dto.getId() != null && dto.getId() > 0) {
            entity = wpProductRepository.findById(dto.getId()).orElseThrow();
        } else {
            entity = new WpProductEntity();

            entity.setSku(genSky());
        }

//        entity.setUnit(dto.getUnit());
        entity.setStockQuantity(dto.getStockQuantity());
        entity.setWeight(dto.getWeight());
        entity.setStatus(dto.getStatus());
        entity.setSaleType(dto.getSaleType());

//         BRAND -----
        if(dto.getBrand() != null) {
            Optional<WpBrandEntity> brand = wpBrandRepository.findBySlug(dto.getBrand().getSlug());
            if(brand.isPresent()) {entity.setBrand(brand.get());}
        }

//        CATEGORY -----
        if(!dto.getCategories().isEmpty()) entity.getCategories().clear();
        for (WpCategoryDetailDto category : dto.getCategories()) {
            WpCategoryEntity catory = wpCategoryRepository.getReferenceById(category.getId());
            entity.getCategories().add(catory);
        }

//        TRANSLATION
        // Вътре в saveProductWithImages метода:

        if (dto.getTranslations() != null) {
            // 1. Изчистваме старите преводи (Hibernate ще ги изтрие заради orphanRemoval = true)
            entity.getTranslations().clear();

            for (WpProductTranslationDto tDto : dto.getTranslations()) {
                WpProductTranslationEntity tEntity = new WpProductTranslationEntity();
                tEntity.setName(tDto.getName());
                tEntity.setDescription(tDto.getDescription());
                tEntity.setShortDescription(tDto.getShortDescription());

                // Важно: Свързваме с продукта
                tEntity.setProduct(entity);

                // Важно: Свързваме с езика
                if (tDto.getLanguage() != null && tDto.getLanguage().getId() != null) {
                    LanguageEntity lang = languageRepository.getReferenceById(tDto.getLanguage().getId());
                    tEntity.setLanguage(lang);
                }

                entity.getTranslations().add(tEntity);
            }
        }

        if (dto.getAddonConfigs() != null) {
            List<WpProductAddonConfigEntity> currentConfigs = entity.getAddonConfig();

            // Създаваме Set от ID-тата на адоните, които идват от Angular (за бърза проверка)
            Set<Long> incomingValueIds = dto.getAddonConfigs().stream()
                    .filter(a -> a.getAddonValue() != null)
                    .map(a -> a.getAddonValue().getId())
                    .collect(Collectors.toSet());

            // А) ИЗТРИВАНЕ: Махаме тези, които вече не присъстват в новия списък
            // Използваме removeIf, за да изтрием обектите от списъка на entity-то
            currentConfigs.removeIf(existing -> !incomingValueIds.contains(existing.getAddonValue().getId()));

            // Б) ДОБАВЯНЕ / ОБНОВЯВАНЕ
            for (WpProductAddonConfigDto aDto : dto.getAddonConfigs()) {
                if (aDto.getAddonValue() == null) continue;

                Long valId = aDto.getAddonValue().getId();

                // Проверяваме дали този адон вече съществува в текущия списък
                Optional<WpProductAddonConfigEntity> existingOpt = currentConfigs.stream()
                        .filter(e -> e.getAddonValue().getId().equals(valId))
                        .findFirst();

                if (existingOpt.isPresent()) {
                    // Вече го има -> само обновяваме цената
                    existingOpt.get().setPriceModifier(aDto.getPriceModifier());
                    // Ако имаш поле active, можеш и него: existingOpt.get().setActive(true);
                } else {
                    // НОВ ЗАПИС: Трябва да го създадем и добавим към колекцията
                    WpProductAddonConfigEntity newConfig = new WpProductAddonConfigEntity();
                    newConfig.setProduct(entity); // Свързваме с текущия продукт
                    newConfig.setAddonValue(wpAddonValueRepository.getReferenceById(valId)); // Само референция към стойността
                    newConfig.setPriceModifier(aDto.getPriceModifier());
                    newConfig.setActive(true);

                    // Добавяме го в списъка
                    currentConfigs.add(newConfig);
                }
            }
        }
        else {
            // Ако от Angular дойде null/празно, чистим всичко
            entity.getAddonConfig().clear();
        }

//        siteConfig
        if(dto.getSiteConfig() != null && !dto.getSiteConfig().isEmpty()) {
            for (WpProductSiteConfigDto wpProductSiteConfigDto : dto.getSiteConfig()) {
                WpProductSiteConfigEntity siteConfig =
                        wpProductSiteConfigRepository.findById(wpProductSiteConfigDto.getId())
                        .orElse(new WpProductSiteConfigEntity());
                SiteEntity site = siteRepository.getReferenceById(wpProductSiteConfigDto.getSite().getId());



                siteConfig.setPrice(wpProductSiteConfigDto.getPrice());
                siteConfig.setRegularPrice(wpProductSiteConfigDto.getRegularPrice());
                siteConfig.setSite(site);
                siteConfig.setProduct(entity);
                wpProductSiteConfigRepository.save(siteConfig);
            }
        }

        entity = wpProductRepository.save(entity);


        // 1. СИНХРОНИЗАЦИЯ НА СНИМКИТЕ (Изтриване на липсващите)
        // Гледаме кои ID-та идват от Angular
        List<Long> incomingIds = dto.getImages().stream()
                .map(WpProductImageDto::getId)
                .filter(id -> id != null && id > 0)
                .toList();

        // Намираме кои снимки от БД липсват в пратката от Angular
        List<WpProductImageEntity> imagesToDelete = entity.getImages().stream()
                .filter(img -> !incomingIds.contains(img.getId()))
                .toList();

        // 2. ФИЗИЧЕСКО ИЗТРИВАНЕ ОТ ПАПКАТА
        for (WpProductImageEntity img : imagesToDelete) {
            fileStorageService.deleteProductImage(img.getLocalSrc());
        }

        // Премахваме от entity-то тези снимки, които не са в изпратения списък
        // Благодарение на orphanRemoval = true, Hibernate ще изтрие тези редове от БД
        entity.getImages().removeIf(img -> !incomingIds.contains(img.getId()));

        if (!dto.getImages().isEmpty()) {
            for (WpProductImageDto imgDto : dto.getImages()) {
                if (imgDto.isTemp()) {
                    String finalPath = fileStorageService.moveTempImageToProductDir(imgDto.getTempName(), entity.getId());
                    if (finalPath != null) {
                        WpProductImageEntity imageEntity = new WpProductImageEntity();
                        imageEntity.setProduct(entity);
                        imageEntity.setLocalSrc(finalPath);
                        wpProductImageRepository.save(imageEntity);
                    }
                }
            }
        }
        try {
            wpProductAsyncService.updateProductOnSites(entity, dto.getLastEditedSiteId());
        } catch (Exception e) {}

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

    @Transactional()
    @Cacheable(value = "productsList", key = "{#pageable, #brand, #category, #name_sku, #quantity, #status, #saleType}")
    public Page<WpProductDto> getAll(
            Pageable pageable,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long quantity,
            @RequestParam(required = false) Long status,
            @RequestParam(required = false) Long saleType,
            @RequestParam(required = false) String name_sku
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

        Page<WpProductEntity> dtoPage = wpProductRepository.findAll(spec, pageable);

        Page<WpProductDto> dtos = dtoPage.map(entity -> {
            WpProductDto wpProductDto = new WpProductDto();
            wpProductDto.setWeight(entity.getWeight());
            wpProductDto.setStockQuantity(entity.getStockQuantity());
            wpProductDto.setId(entity.getId());
            wpProductDto.setBrand(entity.getBrand() != null ? modelMapper.map(entity.getBrand(), WpBrandDto.class) : null);
            wpProductDto.setCategories(entity.getCategories().stream().map(e -> {
                WpCategoryDetailDto map = modelMapper.map(e, WpCategoryDetailDto.class);
                map.setName(e.getTranslations().stream().map(WpCategoryTranslationEntity::getName).collect(Collectors.joining(",")));
                return map;
            }).collect(Collectors.toList()));
            wpProductDto.setSku(entity.getSku());
//
            String names = entity.getTranslations()
                    .stream()
                    .map(WpProductTranslationEntity::getName)
                    .collect(Collectors.joining(" | "));
            wpProductDto.setNames(names);
            wpProductDto.setStatus(entity.getStatus());
            wpProductDto.setSiteConfig(entity.getSiteConfigs().stream().map(e -> modelMapper.map(e, WpProductSiteConfigDto.class)).collect(Collectors.toList()));
            wpProductDto.setSaleType(entity.getSaleType());


            // 2. Безопасна снимка
            if (entity.getImages() != null && !entity.getImages().isEmpty()) {
                // Взимаме първата снимка от списъка на Entity-то
                String localPath = entity.getImages().get(0).getLocalSrc();

                // Базовият URL на твоя сървър
                //                    String baseUrl = "http://192.168.31.232:9494";
                // Тъй като localPath вече започва с /media, просто ги съединяваме
                //                    String fullUrl = localPath;
                // Записваме пълния URL в DTO-то за Angular
                wpProductDto.setM_image(localPath);
            } else {
                wpProductDto.setM_image(null);
            }

            return wpProductDto;
        });

       return dtos;

    }

    @CacheEvict(value = "productsList", allEntries = true)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreQuantity(WpOrderEntity wpOrderEntity) {

        for (OrderLineItem orderLineItem : wpOrderEntity.getOrderLine()) {
            String pSku = orderLineItem.getSku();

            Optional<WpProductHistoryEntity> byProductSku = wpProductHistoryRepository.findByProductSkuAndOrder(pSku, wpOrderEntity);
            if (byProductSku.isPresent()) {
               WpProductHistoryEntity pHistory = byProductSku.get();

                Optional<WpProductEntity> byId = wpProductRepository.findById(pHistory.getProduct().getId());
                if (byId.isPresent()) {
                    WpProductEntity wpProductEntity = byId.get();
                    wpProductEntity.setStockQuantity(wpProductEntity.getStockQuantity() + pHistory.getQuantity());
                    wpProductRepository.save(wpProductEntity);
                    try{
                        wpProductAsyncService.updateProductOnSites(wpProductEntity, null);
                    } catch (Exception e) {}
                }

                wpProductHistoryRepository.delete(pHistory);
            }
        }
    }

    @Transactional
    public void postUpdate(WpProductEntity old, WpProductEntity wpProductEntity) {


//        obnovqvame vsicki saytove





//        Map<String, String> oldNamesMap = old.getTranslations().stream()
//                .collect(Collectors.toMap(
//                        t -> t.getLanguage().getCode(), // Взимаме кода от LanguageEntity
//                        WpProductTranslationEntity::getName,
//                        (v1, v2) -> v1
//                ));
//
//        for (WpProductTranslationEntity currentTrans : wpProductEntity.getTranslations()) {
//            String langCode = currentTrans.getLanguage().getCode();
//            String newName = currentTrans.getName();
//            String oldName = oldNamesMap.get(langCode);
//
//            System.out.println(newName);
//            System.out.println(oldName);
//
//            // Сравняваме
//            if (oldName != null && !oldName.equals(newName)) {
//                System.out.println("Името е променено за език: " + langCode);
//
//                // Извикваш обновяването на външния сайт само за този език
////                syncWithExternalSite(product.getSku(), langCode, newName);
//            }
//        }

    }


//    @Transactional
    public void syncProductsToSite(Long siteId) {
        SiteEntity site = siteRepository.findById(siteId).orElseThrow();
        if(site.getUrl().contains("sateno.bg")) {
            throw new RuntimeException("sateno.bg");
        }

//        try { clearAllProductsFromSite(site); } catch (Exception e) { log.error("Грешка при чистене на продукти: {}", e.getMessage()); }
//        try { clearAllCategoriesFromSite(site); } catch (Exception e) { log.error("Грешка при чистене на категории: {}", e.getMessage()); }
//        try { clearAllBrandsFromSite(site); } catch (Exception e) { log.error("Грешка при чистене на брандове: {}", e.getMessage()); }

        // Взимаме тестовия продукт
//        WpProductEntity product = wpProductRepository.findById(533L).orElseThrow();
        AtomicInteger count = new AtomicInteger(1);
        List<WpProductEntity> allWithAddons = wpProductRepository.findAll();
        ExecutorService executor = Executors.newFixedThreadPool(10);


        for (WpProductEntity wpProductEntity : allWithAddons) {
            executor.submit(() -> {
                try {
//                massSyncAllToSite(wpProductEntity, site);
//                syncSalePriceBySku(site, wpProductEntity);
                    translateProductInfos(wpProductEntity, site);
                    count.getAndIncrement();
//                    System.out.println(count.get());
                }
                catch (Exception e) {
                    // Ако един продукт гръмне, записваме грешката и преминаваме на следващия
                    log.error("КРИТИЧНА ГРЕШКА за продукт SKU {}: {}", wpProductEntity.getSku(), e.getMessage());
                }

            });

        }
        executor.shutdown();

        try {
            // Чакаме нишките да приключат. Сложи достатъчно време (напр. 1 час)
            if (!executor.awaitTermination(10, TimeUnit.HOURS)) {
                executor.shutdownNow(); // Ако не приключат за 1 час, ги спри принудително
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Синхронизацията приключи. Успешно обработени: {} продукта.брой {}", count.get(), allWithAddons.size());


    }

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

    private void massSyncAllToSite(WpProductEntity product, SiteEntity site) {



        WpProductSiteConfigEntity siteConfig = wpProductSiteConfigRepository
                .findByProductAndSite(product, site)
                .orElse(new WpProductSiteConfigEntity());

        WpProductSiteConfigEntity satenoConfig = wpProductSiteConfigRepository
                .findBySiteUrlAndProduct("sateno.bg", product);

        WpProductTranslationEntity translation = wpProductTranslationRepository
                .findByProductAndLanguage(product, site.getLanguage())
                .orElseThrow(() -> new RuntimeException("Липсва превод за този сайт"));

        // --- ЛОГИКА ЗА СНИМКИ ---
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

        // Подготвяме тялото на заявката
        Map<String, Object> body = new HashMap<>();
        body.put("sku", product.getSku());
        body.put("name", translation.getName());
        body.put("type", product.getType());
        body.put("status", product.getStatus().getValue());
        body.put("description", translation.getDescription());
        body.put("short_description", translation.getShortDescription());
//        body.put("regular_price", siteConfig.getRegularPrice() != null ? siteConfig.getRegularPrice().toString() : "0");
        body.put("regular_price", satenoConfig.getRegularPrice() != null ? satenoConfig.getRegularPrice().toString() : "0");
        body.put("price", satenoConfig.getRegularPrice() != null ? satenoConfig.getRegularPrice().toString() : "0");
        body.put("manage_stock", product.isManage_stock());
        body.put("catalog_visibility", product.getCatalog_visibility());
        body.put("stock_quantity", product.isManage_stock()? product.getStockQuantity(): null);
        body.put("featured", product.isFeatured());
        body.put("images", imageList); // ДОБАВЯМЕ СНИМКИТЕ ТУК
        body.put("sale_price", satenoConfig.getSalePrice() != null? satenoConfig.getSalePrice().toString() : "0");
//        if(product.getSaleType() == ProductSaleType.UNLIMITED){
            body.put("stock_status",product.getStock_status());
//        }



        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());


        List<Map<String, Object>> categoryList = uploadCategoriesToWordpress(site, product, auth);
        body.put("categories", categoryList);

        List<Map<String, Object>> brandList = uploadBrandsToWordpress(site, product, auth);
        body.put("brands", brandList);

        try {
            var response = restClient.post()
                    .uri(site.getUrlWithHttps() + PRODUCTS_URL)
                    .header("Authorization", "Basic " + auth)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("id")) {
                Integer wpId = (Integer) response.get("id");
                siteConfig.setProduct(product);
                siteConfig.setSite(site);
                siteConfig.setWpProductId(Long.valueOf(wpId));
                wpProductSiteConfigRepository.save(siteConfig);
                log.info("Успешно създаден продукт в WP с ID: {} и прикачени {} снимки", wpId, imageList.size());
            }

        } catch (Exception e) {
            log.error("Грешка при POST към WooCommerce: {}", e.getMessage());
        }
    }

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

@Transactional
protected void clearAllProductsFromSite(SiteEntity site) {
    String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
    log.info("Започва пълно изчистване на продукти и медия от сайт: {}", site.getUrl());

    while (true) {
        // 1. Взимаме продуктите ЗАЕДНО със снимките им
        var response = restClient.get()
                .uri(site.getUrlWithHttps() + PRODUCTS_URL + "?per_page=100&_fields=id,images")
                .header("Authorization", "Basic " + auth)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        if (response == null || response.isEmpty()) {
            log.info("Няма повече продукти за изтриване.");
            break;
        }

        List<Long> productIds = new ArrayList<>();
        Set<Long> mediaIdsToDelete = new HashSet<>();

        for (Map<String, Object> product : response) {
            productIds.add(Long.valueOf(product.get("id").toString()));

            // Извличаме ID-тата на снимките на този продукт
            List<Map<String, Object>> images = (List<Map<String, Object>>) product.get("images");
            if (images != null) {
                for (Map<String, Object> img : images) {
                    mediaIdsToDelete.add(Long.valueOf(img.get("id").toString()));
                }
            }
        }

        // 2. ИЗТРИВАМЕ ПРОДУКТИТЕ (Batch)
        deleteProductsBatch(site, productIds, auth);

        // 3. ИЗТРИВАМЕ СНИМКИТЕ (Една по една, защото WP Media API няма Batch Delete по подразбиране)
//        deleteMediaOneByOne(site, mediaIdsToDelete, auth);
    }
}

    private void deleteProductsBatch(SiteEntity site, List<Long> ids, String auth) {
        Map<String, Object> deleteBody = Map.of("delete", ids);
        try {
            restClient.post()
                    .uri(site.getUrlWithHttps() + PRODUCTS_URL + "/batch")
                    .header("Authorization", "Basic " + auth)
                    .body(deleteBody)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Успешно изтрити {} продукта.", ids.size());
        } catch (Exception e) {
            log.error("Грешка при batch изтриване на продукти: {}", e.getMessage());
        }
    }

//    private void deleteMediaOneByOne(SiteEntity site, Set<Long> mediaIds, String auth) {
//        for (Long mediaId : mediaIds) {
//            try {
//                // force=true е задължително за медия, за да не отиде в Trash, а да се изтрие физически файлът
//                restClient.delete()
//                        .uri(site.getUrlWithHttps() + "/wp-json/wp/v2/media/" + mediaId + "?force=true")
//                        .header("Authorization", "Basic " + auth)
//                        .retrieve()
//                        .toBodilessEntity();
//            } catch (Exception e) {
//                // Често една снимка е свързана с няколко продукта, затова ако вече е изтрита, просто игнорираме
//                log.warn("Медия ID {} вече не съществува или не може да бъде изтрита.", mediaId);
//            }
//        }
//        log.info("Изчистени {} медийни файла от WordPress.", mediaIds.size());
//    }

    @Transactional
    protected void clearAllCategoriesFromSite(SiteEntity site) {
        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
        log.info("Започва изчистване на категории от сайт: {}", site.getUrl());

        while (true) {
            // Взимаме първите 100 категории
            var response = restClient.get()
                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories?per_page=100&_fields=id")
                    .header("Authorization", "Basic " + auth)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            // Филтрираме ID 18 (обикновено Uncategorized), защото не може да се трие
            List<Long> idsToDelete = response.stream()
                    .map(m -> Long.valueOf(m.get("id").toString()))
                    .filter(id -> id != 18) // Замени 18 с ID-то на твоята default категория ако е различно
                    .toList();

            if (idsToDelete.isEmpty()) break;

            Map<String, Object> deleteBody = Map.of("delete", idsToDelete);

            restClient.post()
                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories/batch")
                    .header("Authorization", "Basic " + auth)
                    .body(deleteBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Изтрити {} категории.", idsToDelete.size());
        }
    }

    @Transactional
    protected void clearAllBrandsFromSite(SiteEntity site) {
        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
        log.info("Започва изчистване на брандове от сайт: {}", site.getUrl());

        while (true) {
            // Внимание: Endpoint-ът трябва да съвпада с този, който ползваш за качване
            var response = restClient.get()
                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/brands?per_page=100&_fields=id")
                    .header("Authorization", "Basic " + auth)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null || response.isEmpty()) break;

            List<Long> idsToDelete = response.stream()
                    .map(m -> Long.valueOf(m.get("id").toString()))
                    .toList();

            Map<String, Object> deleteBody = Map.of("delete", idsToDelete);

            restClient.post()
                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/brands/batch")
                    .header("Authorization", "Basic " + auth)
                    .body(deleteBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Изтрити {} бранда.", idsToDelete.size());
        }
    }





}
