package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpCategoryRepository;
import com.sateno_b.www.model.repository.WpCategorySiteMappingRepository;
import com.sateno_b.www.model.repository.WpCategoryTranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class WpCategoryAsyncService {

    private final SiteRepository siteRepository;
    private final RestClient restClient;
    private final ChatGptService chatGptService;
    private final WpCategoryRepository wpCategoryRepository;
    private final WpCategoryTranslationRepository wpCategoryTranslationRepository;
    private final WpCategorySiteMappingRepository wpCategorySiteMappingRepository;

    @Transactional
    @Async
    public void asyncPostPersist(WpCategoryEntity wpCategoryEntity) throws InterruptedException {
        Thread.sleep(2000);

        wpCategoryEntity = wpCategoryRepository.findById(wpCategoryEntity.getId()).orElse(null);
        if (wpCategoryEntity == null || wpCategoryEntity.getTranslations().isEmpty()) return;

        WpCategoryTranslationEntity baseTranslation = wpCategoryEntity.getTranslations().get(0);
        LanguageEntity baseLanguage = baseTranslation.getLanguage();

        List<SiteEntity> siteList = siteRepository.findAll();
        for (SiteEntity site : siteList) {
            try{
                LanguageEntity siteLanguage = site.getLanguage();
                String targetLanguageName = siteLanguage.getName();

                String translatedName = null;
                if(siteLanguage.equals(baseLanguage)){
                    translatedName = baseLanguage.getName();
                } else {
                    final LanguageEntity finalSiteLanguage = siteLanguage;
                    translatedName = wpCategoryEntity.getTranslations().stream()
                            .filter(t -> t.getLanguage().equals(finalSiteLanguage))
                            .map(WpCategoryTranslationEntity::getName)
                            .findFirst()
                            .orElse(null);

                    if(translatedName == null){
                        String Instruction = String.format("Превод от %s към %s", baseLanguage.getName(), siteLanguage.getName());
                        translatedName = chatGptService.translateText(baseLanguage.getName(), Instruction);

                        WpCategoryTranslationEntity newTranslation = new WpCategoryTranslationEntity();
                        newTranslation.setName(translatedName);
                        newTranslation.setLanguage(siteLanguage);
                        newTranslation.setWpCategory(wpCategoryEntity);
                        wpCategoryTranslationRepository.save(newTranslation);
                        wpCategoryEntity.getTranslations().add(newTranslation);
                    }
                }

                String auth = Base64.getEncoder().encodeToString(
                        (site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

                Map<String, Object> body = new HashMap<>();
                body.put("name", translatedName);

                Map<String, Object> response = restClient.post()
                        .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories")
                        .header("Authorization", "Basic " + auth)
                        .body(body)
                        .retrieve()
                        .body(new org.springframework.core.ParameterizedTypeReference<>() {});

                if (response != null && response.containsKey("id")) {
                    WpCategorySiteMappingEntity mapping = new WpCategorySiteMappingEntity();
                    mapping.setWpCategory(wpCategoryEntity);
                    mapping.setSite(site);
                    mapping.setWpId(Long.valueOf(response.get("id").toString()));
                    mapping.setSlug(response.get("slug").toString());

                    wpCategorySiteMappingRepository.save(mapping);
                    log.info("Успешна синхронизация за сайт {}: WP_ID {}", site.getUrlWithHttps(), mapping.getWpId());
                }
            } catch (Exception e) {
                log.error("Грешка при обработка на сайт {}: {}", site.getUrlWithHttps(), e.getMessage());
            }
        }






    }
}
