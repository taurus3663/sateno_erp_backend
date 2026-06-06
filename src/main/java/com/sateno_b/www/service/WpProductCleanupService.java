package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.repository.WpCategorySiteMappingRepository;
import com.sateno_b.www.model.repository.WpProductImageSiteMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WpProductCleanupService {
    private final WpProductImageSiteMappingRepository wpProductImageSiteMappingRepository;
    private final WpCategorySiteMappingRepository wpCategorySiteMappingRepository;
    private final RestClient restClient;
    private static final String PRODUCTS_URL = "/wp-json/wc/v3/products";

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        try {
            wpProductImageSiteMappingRepository.deleteAllBySite(site);
            log.info("Успешно изтрити локалните мапинги на СНИМКИТЕ за сайт: {}", site.getUrl());
        } catch (Exception e) {
            log.error("Грешка при триене на мапингите за снимки за сайт {}: {}", site.getUrl(), e.getMessage());
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
//                    .filter(id -> id != 18) // Замени 18 с ID-то на твоята default категория ако е различно
                    .toList();

            if (idsToDelete.isEmpty() || idsToDelete.size() == 1) break;

            Map<String, Object> deleteBody = Map.of("delete", idsToDelete);

            restClient.post()
                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories/batch")
                    .header("Authorization", "Basic " + auth)
                    .body(deleteBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Изтрити {} категории.", idsToDelete.size());
        }

        try {
            wpCategorySiteMappingRepository.deleteAllBySite(site);
            log.info("Успешно изтрити локалните мапинги на категориите за сайт: {}", site.getUrl());
        } catch (Exception e) {
            log.error("Грешка при триене на локалните мапинги за сайт {}: {}", site.getUrl(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
