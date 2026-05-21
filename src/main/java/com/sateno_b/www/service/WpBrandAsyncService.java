package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpBrandEntity;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpBrandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WpBrandAsyncService {

    private final SiteRepository siteRepository;
    private final RestClient restClient;
    private final WpBrandRepository wpBrandRepository;

    @Transactional(readOnly = true)
    @Async
    public void updateBrandOnSites(WpBrandEntity brand) {
        try {
            // Малко изчакване за сигурност, за да сме сигурни, че транзакцията в главната нишка е приключила
            Thread.sleep(1000);

            brand = wpBrandRepository.findById(brand.getId()).orElse(null);
            if (brand == null) return;

            // 1. Взимаме всички активни сайтове в системата
            List<SiteEntity> allSites = siteRepository.findAll();

            for (SiteEntity site : allSites) {
                if (site.getUrl() == null || site.getConsumerKey() == null || site.getConsumerSecret() == null) {
                    continue;
                }

                try {
                    // 2. Генерираме Basic Auth за конкретния сайт
                    String authStr = site.getConsumerKey() + ":" + site.getConsumerSecret();
                    String authHeader = "Basic " + Base64.getEncoder().encodeToString(authStr.getBytes());

                    String baseUrl = site.getUrlWithHttps() + "/wp-json/wc/v3/products/brands";

                    // 3. Проверяваме дали този бранд вече съществува в този сайт чрез неговия Slug
                    List<Map<String, Object>> existingBrands = restClient.get()
                            .uri(baseUrl + "?slug=" + brand.getSlug())
                            .header("Authorization", authHeader)
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("name", brand.getName());
                    requestBody.put("slug", brand.getSlug());
                    requestBody.put("description", brand.getDescription()); // добави го, ако имаш описание

                    if (existingBrands != null && !existingBrands.isEmpty()) {
                        // --- СЦЕНАРИЙ А: Брандът СЪЩЕСТВУВА -> АКТУАЛИЗИРАМЕ ГО (PUT) ---
                        Object wpBrandId = existingBrands.get(0).get("id");
                        log.info("Обновяване на бранд '{}' на сайт {} (WP ID: {})", brand.getName(), site.getName(), wpBrandId);

                        restClient.put()
                                .uri(baseUrl + "/" + wpBrandId)
                                .header("Authorization", authHeader)
                                .body(requestBody)
                                .retrieve()
                                .toBodilessEntity();
                    } else {
                        // --- СЦЕНАРИЙ Б: Брандът НЕ СЪЩЕСТВУВА -> СЪЗДАВАМЕ ГО (POST) ---
                        log.info("Създаване на нов бранд '{}' на сайт {}", brand.getName(), site.getName());

                        restClient.post()
                                .uri(baseUrl)
                                .header("Authorization", authHeader)
                                .body(requestBody)
                                .retrieve()
                                .toBodilessEntity();
                    }
                } catch (Exception siteEx) {
                    log.error("Грешка при синхронизация на бранд към сайт {}: {}", site.getName(), siteEx.getMessage());
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Синхронизацията беше прекъсната: {}", ie.getMessage());
        } catch (Exception e) {
            log.error("Грешка в асинхронния процес за брандове: {}", e.getMessage());
        }
    }
}