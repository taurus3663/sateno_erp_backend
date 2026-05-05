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
//                String targetLanguageName = siteLanguage.getName();

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
                        System.out.println(Instruction);
                        translatedName = chatGptService.translateText(baseTranslation.getName(), Instruction);
                        System.out.println(translatedName);
                        WpCategoryTranslationEntity newTranslation = new WpCategoryTranslationEntity();
                        newTranslation.setName(translatedName);
                        newTranslation.setLanguage(siteLanguage);
                        newTranslation.setWpCategory(wpCategoryEntity);
                        wpCategoryTranslationRepository.save(newTranslation);
                        wpCategoryEntity.getTranslations().add(newTranslation);
                    }
                }

                Long wpParentId = 0L; // 0 в WooCommerce означава "Главна категория"
                if (wpCategoryEntity.getParent() != null) {
                    // Търсим в мапинг таблицата какъв е ID-то на родителя В ТОЗИ КОНКРЕТЕН САЙТ
                    wpParentId = wpCategorySiteMappingRepository
                            .findByWpCategoryAndSite(wpCategoryEntity.getParent(), site)
                            .map(WpCategorySiteMappingEntity::getWpId)
                            .orElse(0L);

                    // ВАЖНО: Ако родителят още не е синхронизиран към този сайт, wpParentId ще бъде 0.
                    // Добре е родителските категории да се създават първи.
                }

                String auth = Base64.getEncoder().encodeToString(
                        (site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

                Map<String, Object> body = new HashMap<>();
                body.put("name", translatedName);
                if (wpParentId > 0) {
                    body.put("parent", wpParentId);
                }

                Map<String, Object> response = restClient.post()
                        .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories")
                        .header("Authorization", "Basic " + auth)
                        .body(body)
                        .retrieve()
                        .body(new org.springframework.core.ParameterizedTypeReference<>() {});

                System.out.println(response);
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


    @Transactional
    @Async
    public void asyncUpdate(WpCategoryEntity wpCategoryEntity) throws InterruptedException {
        Thread.sleep(2000);

        wpCategoryEntity = wpCategoryRepository.findById(wpCategoryEntity.getId()).orElse(null);
        if (wpCategoryEntity == null) return;

        WpCategoryTranslationEntity baseTranslation = wpCategoryEntity.getTranslations().get(0);
        LanguageEntity baseLanguage = baseTranslation.getLanguage();

        List<SiteEntity> siteList = siteRepository.findAll();
        for (SiteEntity site : siteList) {
            try {
                // 1. Търсим съществуващ мапинг
                WpCategorySiteMappingEntity mapping = wpCategorySiteMappingRepository
                        .findByWpCategoryAndSite(wpCategoryEntity, site)
                        .orElse(null);

                // 2. Логика за името (превод) - същата като досега
                LanguageEntity siteLanguage = site.getLanguage();
                String targetName = getOrTranslateName(wpCategoryEntity, baseTranslation, baseLanguage, siteLanguage);

                // 3. Логика за родителя (Parent)
                Long wpParentId = 0L;
                if (wpCategoryEntity.getParent() != null) {
                    // ВЗЕМАМЕ РОДИТЕЛЯ ОТ БАЗАТА, ЗА ДА СМЕ СИГУРНИ, ЧЕ Е ТАМ
                    WpCategoryEntity parentEntity = wpCategoryEntity.getParent();

                    // Търсим мапинга на родителя
                    wpParentId = wpCategorySiteMappingRepository
                            .findByWpCategoryAndSite(parentEntity, site)
                            .map(WpCategorySiteMappingEntity::getWpId)
                            .orElse(0L);

                    if (wpParentId == 0) {
                        log.warn("Родителят на категория {} все още няма WP_ID за сайт {}. Категорията ще бъде създадена като главна.",
                                wpCategoryEntity.getId(), site.getUrl());
                    }
                }

                // 4. Подготовка на Auth и Body
                String auth = Base64.getEncoder().encodeToString(
                        (site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

                Map<String, Object> body = new HashMap<>();
                body.put("name", targetName);
                body.put("parent", wpParentId);

                if (mapping != null) {
                    // --- UPDATE ---
                    restClient.put()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories/" + mapping.getWpId())
                            .header("Authorization", "Basic " + auth)
                            .body(body)
                            .retrieve()
                            .toBodilessEntity();
                    log.info("Успешно обновен сайт {}: WP_ID {}", site.getUrlWithHttps(), mapping.getWpId());

                } else {
                    // --- CREATE (Ако мапингът липсва) ---
                    Map<String, Object> response = restClient.post()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories")
                            .header("Authorization", "Basic " + auth)
                            .body(body)
                            .retrieve()
                            .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

                    if (response != null && response.containsKey("id")) {
                        WpCategorySiteMappingEntity newMapping = new WpCategorySiteMappingEntity();
                        newMapping.setWpCategory(wpCategoryEntity);
                        newMapping.setSite(site);
                        newMapping.setWpId(Long.valueOf(response.get("id").toString()));
                        newMapping.setSlug(response.get("slug").toString());

                        wpCategorySiteMappingRepository.save(newMapping);
                        log.info("Създадена липсваща категория в сайт {}: WP_ID {}", site.getUrlWithHttps(), newMapping.getWpId());
                    }
                }

            } catch (Exception e) {
                log.error("Грешка при обработка на сайт {}: {}", site.getUrlWithHttps(), e.getMessage());
            }
        }
    }

    // Помощен метод за избягване на повторение на код (Refactoring)
    private String getOrTranslateName(WpCategoryEntity entity, WpCategoryTranslationEntity base, LanguageEntity baseLang, LanguageEntity siteLang) {
        if (siteLang.equals(baseLang)) return base.getName();

        String translated = entity.getTranslations().stream()
                .filter(t -> t.getLanguage().equals(siteLang))
                .map(WpCategoryTranslationEntity::getName)
                .findFirst()
                .orElse(null);

        if (translated == null) {
            String instruction = String.format("Translate from %s to %s", baseLang.getName(), siteLang.getName());
            translated = chatGptService.translateText(base.getName(), instruction);

            WpCategoryTranslationEntity newT = new WpCategoryTranslationEntity();
            newT.setName(translated);
            newT.setLanguage(siteLang);
            newT.setWpCategory(entity);
            wpCategoryTranslationRepository.save(newT);
        }
        return translated;
    }

    public boolean syncWpCategoryToSite(Long siteId) {
        List<WpCategoryEntity> all = wpCategoryRepository.findAll();

        all.sort((a, b) -> {
            if (a.getParent() == null && b.getParent() != null) return -1;
            if (a.getParent() != null && b.getParent() == null) return 1;
            return 0;
        });

        SiteEntity site = siteRepository.findById(siteId).orElse(null);
        if(site == null) return false;

        for (WpCategoryEntity wpCategoryEntity : all) {

            WpCategoryTranslationEntity baseTranslation = wpCategoryEntity.getTranslations().get(0);
            LanguageEntity baseLanguage = baseTranslation.getLanguage();

//            List<SiteEntity> siteList = siteRepository.findAll();
//            for (SiteEntity site : siteList) {

                try {
                    // 1. Търсим съществуващ мапинг
                    WpCategorySiteMappingEntity mapping = wpCategorySiteMappingRepository
                            .findByWpCategoryAndSite(wpCategoryEntity, site)
                            .orElse(null);

                    // 2. Логика за името (превод) - същата като досега
                    LanguageEntity siteLanguage = site.getLanguage();
                    String targetName = getOrTranslateName(wpCategoryEntity, baseTranslation, baseLanguage, siteLanguage);

                    // 3. Логика за родителя (Parent)
                    Long wpParentId = 0L;
                    if (wpCategoryEntity.getParent() != null) {
                        // ВЗЕМАМЕ РОДИТЕЛЯ ОТ БАЗАТА, ЗА ДА СМЕ СИГУРНИ, ЧЕ Е ТАМ
                        WpCategoryEntity parentEntity = wpCategoryEntity.getParent();

                        // Търсим мапинга на родителя
                        wpParentId = wpCategorySiteMappingRepository
                                .findByWpCategoryAndSite(parentEntity, site)
                                .map(WpCategorySiteMappingEntity::getWpId)
                                .orElse(0L);

                        if (wpParentId == 0) {
                            log.warn("Родителят на категория {} все още няма WP_ID за сайт {}. Категорията ще бъде създадена като главна.",
                                    wpCategoryEntity.getId(), site.getUrl());
                        }
                    }

                    // 4. Подготовка на Auth и Body
                    String auth = Base64.getEncoder().encodeToString(
                            (site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

                    Map<String, Object> body = new HashMap<>();
                    body.put("name", targetName);
                    body.put("parent", wpParentId);

                    if (mapping != null) {
                        // --- UPDATE ---
                        restClient.put()
                                .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories/" + mapping.getWpId())
                                .header("Authorization", "Basic " + auth)
                                .body(body)
                                .retrieve()
                                .toBodilessEntity();
                        log.info("Успешно обновен сайт {}: WP_ID {}", site.getUrlWithHttps(), mapping.getWpId());

                    } else {
                        // --- CREATE (Ако мапингът липсва) ---
                        Map<String, Object> response = restClient.post()
                                .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/categories")
                                .header("Authorization", "Basic " + auth)
                                .body(body)
                                .retrieve()
                                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

                        if (response != null && response.containsKey("id")) {
                            WpCategorySiteMappingEntity newMapping = new WpCategorySiteMappingEntity();
                            newMapping.setWpCategory(wpCategoryEntity);
                            newMapping.setSite(site);
                            newMapping.setWpId(Long.valueOf(response.get("id").toString()));
                            newMapping.setSlug(response.get("slug").toString());

                            wpCategorySiteMappingRepository.save(newMapping);
                            log.info("Създадена липсваща категория в сайт {}: WP_ID {}", site.getUrlWithHttps(), newMapping.getWpId());
                        }
                    }

                } catch (Exception e) {
                    log.error("Грешка при обработка на сайт {}: {}", site.getUrlWithHttps(), e.getMessage());
                }
//            }
        }

        return true;
    }

}
