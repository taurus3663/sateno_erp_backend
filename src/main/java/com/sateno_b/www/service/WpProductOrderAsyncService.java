package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.WpProductOrderDTO;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpCategorySiteMappingEntity;
import com.sateno_b.www.model.entity.WpProductEntity;
import com.sateno_b.www.model.entity.WpProductOrderEntity;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpCategorySiteMappingRepository;
import com.sateno_b.www.model.repository.WpProductOrderRepository;
import com.sateno_b.www.model.repository.WpProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class WpProductOrderAsyncService {

    private final SiteRepository siteRepository;
    private final RestClient restClient;
    private final WpProductOrderRepository wpProductOrderRepository;
    private final WpProductRepository wpProductRepository;
    private final WpCategorySiteMappingRepository wpCategorySiteMappingRepository;

    @Async
    public void updateWpProductOrder(WpProductOrderEntity entity) {
        List<SiteEntity> sites = siteRepository.findByActiveTrue();

        for (SiteEntity site : sites) {
            try {
                String auth = Base64.getEncoder()
                        .encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

                // 1. За всеки локален продукт търсим WooCommerce ID по SKU
                List<Long> wpProductIds = new ArrayList<>();
                for (Long productId : entity.getProductIds()) {
                    WpProductEntity product = wpProductRepository.findById(productId).orElse(null);
                    if (product == null || product.getSku() == null) {
                        log.warn("Продукт {} няма SKU, пропускаме", productId);
                        continue;
                    }

                    List<Map> results = restClient.get()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products?sku=" + product.getSku())
                            .header("Authorization", "Basic " + auth)
                            .retrieve()
                            .body(List.class);

                    if (results != null && !results.isEmpty()) {
                        Integer wpId = (Integer) results.get(0).get("id");
                        wpProductIds.add(wpId.longValue());
                    } else {
                        log.warn("SKU {} не е намерен в сайт {}", product.getSku(), site.getUrl());
                    }
                }
                
                Long categoryId = 0L;
                Optional<WpCategorySiteMappingEntity> byWpCategoryAndSite = wpCategorySiteMappingRepository.findByWpCategoryAndSite(entity.getCategory(), site);
                    if(byWpCategoryAndSite.isPresent()) {
                        categoryId = byWpCategoryAndSite.get().getWpId();
                    }
                // 2. Записваме през custom PHP endpoint
                restClient.post()
                        .uri(site.getUrlWithHttps() + "/wp-json/wc-ext/v1/featured-products")
                        .header("Authorization", "Basic " + auth)
                        .header("Content-Type", "application/json")
                        .body(Map.of(
                                "category_id", categoryId,
                                "product_ids", wpProductIds
                        ))
                        .retrieve()
                        .toBodilessEntity();

                log.info("Успешно обновен сайт {} за категория {}", site.getUrl(), entity.getCategory().getId());

            } catch (Exception e) {
                log.error("Грешка при обновяване на сайт {}: {}", site.getUrl(), e.getMessage());
            }
        }
    }
}
