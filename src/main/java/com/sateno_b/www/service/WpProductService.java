package com.sateno_b.www.service;


import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.enums.ProductSaleType;
import com.sateno_b.www.model.enums.ProductStatus;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.shared.AuthTool;
import com.sateno_b.www.shared.SlugTool;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import java.util.*;
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

    private static final String PRODUCTS_URL = "/wp-json/wc/v3/products";


    @Transactional
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
        WpProductEntity product = wpProductRepository.findBySkuAndSite(dto.getSku(), site.getId())
                .orElseGet(() -> wpProductRepository.save(new WpProductEntity()));

        // 2. Обновяваме Глобалните данни (технически)
        product.setStockQuantity(dto.getStock_quantity());
        product.setWeight(dto.getWeight());
        product.setStatus(dto.getStatus());
        product.setSaleType(dto.isManage_stock() ? ProductSaleType.LIMITED: ProductSaleType.UNLIMITED);

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
        config.setSku(dto.getSku()); // SKU-то от този конкретен сайт
        config.setSlug(SlugTool.decodeSlug(dto.getSlug()));
        config.setWpProductId(dto.getId());

        wpProductSiteConfigRepository.save(config);
    }

    private void syncImagesForProduct(WpProductEntity product, List<WooProductImageDto> wooImages, SiteEntity site) {
        if (wooImages == null) return;

        for (WooProductImageDto wooImg : wooImages) {
            // Проверяваме дали тази снимка (по WP Media ID) вече е свалена за ТОЗИ сайт
            boolean alreadyExists = wpProductImageSiteMappingRepository.existsByWpMediaIdAndSite(wooImg.getId(), site);

            if (!alreadyExists) {
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
            }
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
                WpProductAddonConfigEntity config = product.getAddonConfig().stream()
                        .filter(c -> c.getSite().getId().equals(site.getId()) &&
                                c.getAddonValue().getId().equals(valEntity.getId()))
                        .findFirst()
                        .orElseGet(() -> {
                            WpProductAddonConfigEntity newConfig = new WpProductAddonConfigEntity();
                            newConfig.setProduct(product);
                            newConfig.setAddonValue(valEntity);
                            newConfig.setSite(site);
                            product.getAddonConfig().add(newConfig); // Добавяме към списъка на продукта
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

    @Transactional
    public WpProductDto saveProductWithImages(WpProductDto dto) {
        WpProductEntity entity;
        if (dto.getId() != null && dto.getId() > 0) {
            entity = wpProductRepository.findById(dto.getId()).orElseThrow();
        } else {
            entity = new WpProductEntity();
        }

//        entity.setUnit(dto.getUnit());
        entity.setStockQuantity(dto.getStockQuantity());
        entity.setWeight(dto.getWeight());
        entity.setStatus(dto.getStatus());
        entity.setSaleType(dto.getSaleType());

//         BRAND -----
        Optional<WpBrandEntity> brand = wpBrandRepository.findBySlug(dto.getBrand().getSlug());
        if(brand.isPresent()) {entity.setBrand(brand.get());}

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

//      ADDONS
// ADDONS - Синхронизация само по ID
        if (dto.getAddonConfigs() != null) {
            List<WpProductAddonConfigEntity> currentConfigs = entity.getAddonConfig();

            // 1. Създаваме Set от ключове (SiteID + ValueID) от Angular
            Set<String> incomingKeys = dto.getAddonConfigs().stream()
                    .map(c -> c.getSite().getId() + "-" + c.getAddonValue().getId())
                    .collect(Collectors.toSet());

            // 2. ИЗТРИВАНЕ: Махаме всичко от базата, което липсва в новия списък
            currentConfigs.removeIf(existing -> {
                String key = existing.getSite().getId() + "-" + existing.getAddonValue().getId();
                return !incomingKeys.contains(key);
            });

            // 3. ДОБАВЯНЕ / ОБНОВЯВАНЕ
            for (WpProductAddonConfigDto aDto : dto.getAddonConfigs()) {
                String key = aDto.getSite().getId() + "-" + aDto.getAddonValue().getId();

                // Проверяваме дали тази връзка вече съществува
                Optional<WpProductAddonConfigEntity> existingOpt = currentConfigs.stream()
                        .filter(e -> (e.getSite().getId() + "-" + e.getAddonValue().getId()).equals(key))
                        .findFirst();

                if (existingOpt.isPresent()) {
                    // Вече съществува -> само обновяваме цената
                    existingOpt.get().setPriceModifier(aDto.getPriceModifier());
                } else {
                    // НОВ ЗАПИС: Трябват ни само ID-тата
                    WpProductAddonConfigEntity newConfig = new WpProductAddonConfigEntity();
                    newConfig.setProduct(entity);
                    newConfig.setPriceModifier(aDto.getPriceModifier());
                    newConfig.setActive(true);

                    // Тук е важната част: ползваме само ID-тата за връзка
                    newConfig.setSite(siteRepository.getReferenceById(aDto.getSite().getId()));
                    newConfig.setAddonValue(wpAddonValueRepository.getReferenceById(aDto.getAddonValue().getId()));

                    currentConfigs.add(newConfig);
                }
            }
        } else {
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

    public Page<WpProductEntity> getAll(
            Pageable pageable,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long quantity,
            @RequestParam(required = false) Long status
    ) {
//        Pageable pageable1 = PageRequest.of(
//                pageable.getPageNumber(),
//                pageable.getPageSize(),
//                Sort.by("id").descending()
//        );

        Specification<WpProductEntity> spec = (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            if (sku != null && !sku.isEmpty()) {
                Join<WpProductEntity, WpProductSiteConfigEntity> siteConfig = root.join("siteConfigs");

                predicates.add(cb.like(cb.lower(siteConfig.get("sku")), "%" + sku.toLowerCase() + "%"));
            }
            if (category != null && !category.isEmpty()) {
                // 1. Използваме LIKE вместо EQUAL за частично търсене
                // 2. Използваме cb.lower(), за да сме сигурни, че търсенето не зависи от главни/малки букви
                Join<WpProductEntity, WpCategoryEntity> categoriesJoin = root.join("categories");
                Join<WpCategoryEntity, WpCategoryTranslationEntity> translationsJoin = categoriesJoin.join("translations");

                predicates.add(cb.like(
                        cb.lower(translationsJoin.get("name")),
                        "%" + category.toLowerCase() + "%"
                ));

                // Силно препоръчително: добави distinct, за да не се дублират продуктите
                query.distinct(true);
            }
            if (brand != null && !brand.isEmpty()) {
                // Ако brand е вложен обект: root.join("brand").get("name")
                predicates.add(cb.equal(root.get("brand"), brand));
            }
            if (name != null && !name.isEmpty()) {
                Join<Object, Object> translationsJoin = root.join("translations");
                predicates.add(cb.like(cb.lower(translationsJoin.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (quantity != null) {
                predicates.add(cb.equal(root.get("stockQuantity"), quantity));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }


            return cb.and(predicates.toArray(new Predicate[0]));
        };

       return wpProductRepository.findAll(spec, pageable);

    }

}
