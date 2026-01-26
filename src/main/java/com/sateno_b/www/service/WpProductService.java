package com.sateno_b.www.service;


import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.enums.ProductStatus;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.shared.AuthTool;
import com.sateno_b.www.shared.SlugTool;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WpProductService {

    private final SiteRepository siteRepository;
    private final RestClient restClient;
    private final WpProductRepository wpProductRepository;
    private final WpProductImageRepository wpProductImageRepository;
    private final WpProductAddonConfigRepository wpProductAddonConfigRepository;
    private final WpProductAddonValuePriceRepository wpProductAddonValuePriceRepository;
    private final WpProductImageSiteMappingRepository wpProductImageSiteMappingRepository;
    private final WpProductTranslationRepository wpProductTranslationRepository;
    private final WpBrandRepository wpBrandRepository;
    private final WpBrandWpIdRepository wpBrandWpIdRepository;
    private final WpCategoryRepository wpCategoryRepository;
    private final WpAddonRepository wpAddonRepository;

    private static final String PRODUCTS_URL = "/wp-json/wc/v3/products";


    @Transactional
    public void syncProductsToDB(Long siteId) {
        SiteEntity site = siteRepository.findById(siteId).orElseThrow();
//        String auth = AuthTool.getAuth(site.getConsumerKey(), site.getConsumerSecret());
        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
        LanguageEntity language = site.getLanguage();

        // get All Products
        List<WooProductDto> Products = fetchAllProducts(site, auth);

        for (WooProductDto product : Products) {
            try {

                processSingleProduct(product, site, language);

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
                    .uri(site.getUrl() + PRODUCTS_URL + "?per_page=100&page=" + currentPage + "&orderby=id&order=asc")
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


//        for (WooBrandDto brand : dto.getBrands()) {
//            syncBrandForProduct(brand, site);
//        }
//
//        for (WooProductCategoryDto category : dto.getCategories()) {
//            syncCategoriesForProduct(category, site);
//        }

// 1. Намираме или създаваме Глобалния продукт по SKU
        WpProductEntity product = wpProductRepository.findBySkuAndSite(dto.getSku(), site.getId())
                .orElseGet(() -> wpProductRepository.save(new WpProductEntity()));

        // 2. Обновяваме Глобалните данни (технически)
        product.setStockQuantity(dto.getStock_quantity());
        product.setWeight(dto.getWeight());
        product.setStatus(dto.getStatus());

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

        // 6. СНИМКИ (Локално сваляне)
        syncImagesForProduct(product, dto.getImages(), site);

        // 7. АДОНИ (Специфични за продукта и сайта)
//        syncAddonsForProduct(product, dto.getAddons(), site, lang);



    }

    private void updateTranslation(WpProductEntity product, WooProductDto dto, SiteEntity site, LanguageEntity lang) {
        // Търсим съществуващ превод за този продукт на този сайт и език
        WpProductTranslationEntity translation = wpProductTranslationRepository
                .findByProductAndSiteAndLanguage(product, site, lang)
                .orElse(new WpProductTranslationEntity());

        translation.setProduct(product);
        translation.setSite(site);
        translation.setLanguage(lang);

        // Данни от WooCommerce
        translation.setWpProductId(dto.getId());
        translation.setName(dto.getName());
        translation.setSku(dto.getSku());
        translation.setSlug(SlugTool.decodeSlug(dto.getSlug()));

        // Преобразуваме цените (ако са празни в WP, слагаме 0)
        translation.setPrice(parsePrice(dto.getPrice()));
        translation.setRegularPrice(parsePrice(dto.getRegular_price()));

        translation.setDescription(dto.getDescription());
        translation.setShortDescription(dto.getShort_description());

        wpProductTranslationRepository.save(translation);
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

//    private void syncAddonsForProduct(WpProductEntity product, List<WooAddonDto> wooAddons, SiteEntity site, LanguageEntity lang) {
//        if (wooAddons == null) return;
//
//        for (WooAddonDto wooAddon : wooAddons) {
//            // 1. Намираме или създаваме Глобалния Адон (напр. "Размер на чаршаф")
//            WpAddonEntity addon = wpAddonRepository.findByNameAndLanguage(wooAddon.getName(), lang)
//                    .orElseGet(() -> wpAddonRepository.save(new WpAddonEntity(wooAddon.getName(), wooAddon.getType())));
//
//            // Добавяме го към продукта, ако го няма (Set се грижи за уникалността)
//            product.getAddons().add(addon);
//
//            for (WooAddonOptionsDto opt : wooAddon.getOptions()) {
//                // 2. Намираме или създаваме Глобалната Стойност (напр. "С ластик 120х200")
//                WpAddonValueEntity val = wpAddonValueRepository.findByNameAndAddon(opt.getLabel(), addon)
//                        .orElseGet(() -> wpAddonValueRepository.save(new WpAddonValueEntity(opt.getLabel(), addon)));
//
//                product.getAddonValues().add(val);
//
//                // 3. Записваме специфичната надценка в Config таблицата
//                WpProductAddonConfigEntity config = wpProductAddonConfigRepository
//                        .findByProductAndAddonValueAndSite(product, val, site)
//                        .orElse(new WpProductAddonConfigEntity());
//
//                config.setProduct(product); // Увери се, че си сменил Long productId на Entity в класа
//                config.setAddonValue(val);
//                config.setSite(site);
//                config.setPriceModifier(parsePrice(opt.getPrice()));
//                config.setActive(true);
//
//                wpProductAddonConfigRepository.save(config);
//            }
//        }
//    }

    // Помощна функция за парсване на цена
    private java.math.BigDecimal parsePrice(String price) {
        if (price == null || price.isEmpty()) return java.math.BigDecimal.ZERO;
        try {
            return new java.math.BigDecimal(price);
        } catch (Exception e) {
            return java.math.BigDecimal.ZERO;
        }
    }
}
