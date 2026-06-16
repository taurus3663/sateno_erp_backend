package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WpAttributeSyncService {

    private final SiteRepository siteRepository;
    private final RestClient restClient;
    private final WpAttributeTypeRepository attributeTypeRepository;
    private final WpAttributeTypeTranslationRepository attributeTypeTranslationRepository;
    private final WpAttributeValueRepository attributeValueRepository;
    private final WpAttributeValueTranslationRepository attributeValueTranslationRepository;
    private final LanguageRepository languageRepository;
    private final WpAttributeTranslationService translationService;
    private final WpProductRepository wpProductRepository;

    // ── PULL: WP → ERP ───────────────────────────────────────────────────────
    // Стъпка 1: записва типове и стойности само с езика на сайта (бързо)
    // Стъпка 2: async превод с ChatGPT за останалите езици

    @Transactional
    public void pullFromSite(Long siteId) {
        try {
            SiteEntity site = siteRepository.findById(siteId)
                    .orElseThrow(() -> new RuntimeException("Site not found: " + siteId));

            LanguageEntity sourceLang = site.getLanguage();
            if (sourceLang == null) {
                log.warn("ATTR SYNC → site {} has no language set, aborting", siteId);
                return;
            }

            String auth = buildAuth(site);
            String baseUrl = site.getUrlWithHttps() + "/wp-json/wc/v3/products/attributes";

            log.info("ATTR SYNC → pulling from {} (lang={})", baseUrl, sourceLang.getCode());

            List<Map<String, Object>> wpTypes = restClient.get()
                    .uri(baseUrl + "?per_page=100")
                    .header("Authorization", auth)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            log.info("ATTR SYNC → received {} attribute types", wpTypes == null ? 0 : wpTypes.size());

            if (wpTypes == null || wpTypes.isEmpty()) {
                log.warn("ATTR SYNC → no attributes found on site {}", site.getUrl());
                return;
            }

            for (Map<String, Object> wpType : wpTypes) {
                int wpId = (int) wpType.get("id");
                String slug = (String) wpType.get("slug");
                String name = (String) wpType.get("name");

                log.info("ATTR SYNC → type slug='{}' name='{}'", slug, name);

                WpAttributeTypeEntity typeEntity = attributeTypeRepository.findBySlug(slug)
                        .orElseGet(() -> {
                            WpAttributeTypeEntity t = new WpAttributeTypeEntity();
                            t.setSlug(slug);
                            return attributeTypeRepository.save(t);
                        });

                // Записваме само превода на езика на сайта
                upsertTypeTranslation(typeEntity, sourceLang, name);
                pullTermsForType(auth, baseUrl, wpId, typeEntity, sourceLang);
            }

            log.info("ATTR SYNC → all types/values saved, pulling product attribute links...");
            pullProductAttributeLinks(site, sourceLang);

            log.info("ATTR SYNC → all saved, launching background translation...");

            // Стъпка 2: преводи за останалите езици в отделен bean (реален @Async)
            translationService.translateAll(sourceLang.getCode());

        } catch (Exception e) {
            log.error("ATTR SYNC → FAILED: {}", e.getMessage(), e);
        }
    }

    private void pullTermsForType(String auth, String baseUrl, int wpAttributeId,
                                   WpAttributeTypeEntity typeEntity, LanguageEntity sourceLang) {
        try {
            List<Map<String, Object>> terms = restClient.get()
                    .uri(baseUrl + "/" + wpAttributeId + "/terms?per_page=100")
                    .header("Authorization", auth)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (terms == null) return;

            List<WpAttributeValueEntity> existingValues =
                    attributeValueRepository.findAllByAttributeTypeId(typeEntity.getId());

            for (Map<String, Object> term : terms) {
                String slug = (String) term.get("slug");
                String name = (String) term.get("name");

                WpAttributeValueEntity valueEntity = existingValues.stream()
                        .filter(v -> v.getSlug().equals(slug))
                        .findFirst()
                        .orElseGet(() -> {
                            WpAttributeValueEntity v = new WpAttributeValueEntity();
                            v.setSlug(slug);
                            v.setAttributeType(typeEntity);
                            return attributeValueRepository.save(v);
                        });

                // Записваме само превода на езика на сайта
                upsertValueTranslation(valueEntity, sourceLang, name);
            }
        } catch (Exception e) {
            log.error("ATTR SYNC → error pulling terms for type {}: {}", typeEntity.getSlug(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void pullProductAttributeLinks(SiteEntity site, LanguageEntity sourceLang) {
        String auth = buildAuth(site);
        String baseUrl = site.getUrlWithHttps() + "/wp-json/wc/v3/products";
        String langCode = sourceLang.getCode();
        int page = 1;
        int totalLinked = 0;

        while (true) {
            List<Map<String, Object>> products = restClient.get()
                    .uri(baseUrl + "?per_page=100&page=" + page)
                    .header("Authorization", auth)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (products == null || products.isEmpty()) break;
            log.info("ATTR SYNC → products page {} → {} items", page, products.size());

            for (Map<String, Object> wpProduct : products) {
                String sku = (String) wpProduct.get("sku");
                if (sku == null || sku.isBlank()) continue;

                WpProductEntity product = wpProductRepository.findBySku(sku).orElse(null);
                if (product == null) continue;

                List<Map<String, Object>> attrs = (List<Map<String, Object>>) wpProduct.get("attributes");
                if (attrs == null || attrs.isEmpty()) {
                    product.getAttributeValues().clear();
                    wpProductRepository.save(product);
                    continue;
                }

                List<WpAttributeValueEntity> linkedValues = new ArrayList<>();

                for (Map<String, Object> attr : attrs) {
                    String typeSlug = (String) attr.get("slug");
                    List<String> options = (List<String>) attr.get("options");
                    if (typeSlug == null || options == null || options.isEmpty()) continue;

                    // Ако има повече от 1 стойност → типа е multipleValues
                    if (options.size() > 1) {
                        attributeTypeRepository.findBySlug(typeSlug).ifPresent(type -> {
                            if (!type.isMultipleValues()) {
                                type.setMultipleValues(true);
                                attributeTypeRepository.save(type);
                                log.info("ATTR SYNC → type '{}' → multipleValues=true", typeSlug);
                            }
                        });
                    }

                    for (String optionName : options) {
                        String trimmed = optionName == null ? null : optionName.trim();
                        if (trimmed == null || trimmed.isBlank()) continue;
                        attributeValueRepository.findByTypeSlugAndLabelAndLang(typeSlug, trimmed, langCode)
                                .ifPresentOrElse(
                                        linkedValues::add,
                                        () -> log.warn("ATTR SYNC → no value for type='{}' label='{}' lang='{}'", typeSlug, trimmed, langCode)
                                );
                    }
                }

                product.getAttributeValues().clear();
                product.getAttributeValues().addAll(linkedValues);
                wpProductRepository.save(product);
                totalLinked++;
            }

            if (products.size() < 100) break;
            page++;
        }

        log.info("ATTR SYNC → product attribute links done — {} products updated", totalLinked);
    }

    // ── PUSH: ERP → WP (автоматично при save) ────────────────────────────────

    @Async
    public void pushTypeToAllSites(WpAttributeTypeEntity type) {
        List<SiteEntity> sites = siteRepository.findAll();
        for (SiteEntity site : sites) {
            if (!hasCreds(site) || site.getLanguage() == null) continue;
            try {
                String auth = buildAuth(site);
                String baseUrl = site.getUrlWithHttps() + "/wp-json/wc/v3/products/attributes";
                String label = translationService.ensureTypeLabel(type.getId(), site.getLanguage());

                List<Map<String, Object>> existing = restClient.get()
                        .uri(baseUrl + "?slug=" + type.getSlug())
                        .header("Authorization", auth)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                Map<String, Object> body = new HashMap<>();
                body.put("name", label);
                body.put("slug", type.getSlug());
                body.put("type", "select");
                body.put("order_by", "menu_order");
                body.put("has_archives", false);

                if (existing != null && !existing.isEmpty()) {
                    int wpId = (int) existing.get(0).get("id");
                    restClient.put().uri(baseUrl + "/" + wpId)
                            .header("Authorization", auth).body(body).retrieve().toBodilessEntity();
                    log.info("Updated attribute type '{}' on site {}", type.getSlug(), site.getUrl());
                } else {
                    restClient.post().uri(baseUrl)
                            .header("Authorization", auth).body(body).retrieve().toBodilessEntity();
                    log.info("Created attribute type '{}' on site {}", type.getSlug(), site.getUrl());
                }
            } catch (Exception e) {
                log.error("Error pushing type '{}' to site {}: {}", type.getSlug(), site.getUrl(), e.getMessage());
            }
        }
    }

    @Async
    public void pushValueToAllSites(WpAttributeValueEntity value) {
        List<SiteEntity> sites = siteRepository.findAll();
        String typeSlug = value.getAttributeType().getSlug();

        for (SiteEntity site : sites) {
            if (!hasCreds(site) || site.getLanguage() == null) continue;
            try {
                String auth = buildAuth(site);
                String baseUrl = site.getUrlWithHttps() + "/wp-json/wc/v3/products/attributes";
                String label = translationService.ensureValueLabel(value.getId(), site.getLanguage());

                List<Map<String, Object>> wpTypes = restClient.get()
                        .uri(baseUrl + "?slug=" + typeSlug)
                        .header("Authorization", auth)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                if (wpTypes == null || wpTypes.isEmpty()) {
                    log.warn("Attribute type '{}' not found on site {}", typeSlug, site.getUrl());
                    continue;
                }

                int wpTypeId = (int) wpTypes.get(0).get("id");
                String termsUrl = baseUrl + "/" + wpTypeId + "/terms";

                List<Map<String, Object>> existing = restClient.get()
                        .uri(termsUrl + "?slug=" + value.getSlug())
                        .header("Authorization", auth)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                Map<String, Object> body = new HashMap<>();
                body.put("name", label);
                body.put("slug", value.getSlug());

                if (existing != null && !existing.isEmpty()) {
                    int wpTermId = (int) existing.get(0).get("id");
                    restClient.put().uri(termsUrl + "/" + wpTermId)
                            .header("Authorization", auth).body(body).retrieve().toBodilessEntity();
                    log.info("Updated attribute value '{}' on site {}", value.getSlug(), site.getUrl());
                } else {
                    restClient.post().uri(termsUrl)
                            .header("Authorization", auth).body(body).retrieve().toBodilessEntity();
                    log.info("Created attribute value '{}' on site {}", value.getSlug(), site.getUrl());
                }
            } catch (Exception e) {
                log.error("Error pushing value '{}' to site {}: {}", value.getSlug(), site.getUrl(), e.getMessage());
            }
        }
    }

    // ── BULK PUSH: всички атрибути + продукти → всички сайтове ──────────────
    @Transactional
    public void pushAllToAllSites(List<Long> siteIds) {
        List<SiteEntity> sites = siteRepository.findAllById(siteIds).stream()
                .filter(this::hasCreds)
                .filter(s -> s.getLanguage() != null)
                .toList();
        if (sites.isEmpty()) return;

        // 1 & 2: Push типове и стойности (временно изключено)
        /*
        List<WpAttributeTypeEntity> types = attributeTypeRepository.findAll();
        List<WpAttributeValueEntity> values = attributeValueRepository.findAll();
        types.forEach(t -> t.getTranslations().size());
        values.forEach(v -> { v.getTranslations().size(); v.getAttributeType().getSlug(); });
        for (SiteEntity site : sites) { ... }
        */

        // 3. Push product-attribute връзки
        List<WpProductEntity> products = wpProductRepository.findAll();
        log.info("ATTR PUSH → product attribute links for {} products", products.size());
        int count = 0;
        for (WpProductEntity product : products) {
            if (product.getAttributeValues() == null || product.getAttributeValues().isEmpty()) continue;
            product.getAttributeValues().forEach(av -> {
                av.getTranslations().size();
                av.getAttributeType().getTranslations().size();
            });

            for (SiteEntity site : sites) {
                try {
                    String auth = buildAuth(site);
                    String attrBaseUrl = site.getUrlWithHttps() + "/wp-json/wc/v3/products/attributes";

                    // Зареждаме ВЕДНЪЖ всички WC типове → Map<slug, wcId>
                    List<Map<String, Object>> allWcTypes = restClient.get()
                            .uri(attrBaseUrl + "?per_page=100")
                            .header("Authorization", auth)
                            .retrieve().body(new ParameterizedTypeReference<>() {});
                    Map<String, Integer> wcTypeIdBySlug = new HashMap<>();
                    if (allWcTypes != null) {
                        allWcTypes.forEach(wt -> wcTypeIdBySlug.put((String) wt.get("slug"), (Integer) wt.get("id")));
                    }

                    List<Map<String, Object>> wpProducts = restClient.get()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products?sku=" + product.getSku())
                            .header("Authorization", auth)
                            .retrieve().body(new ParameterizedTypeReference<>() {});
                    if (wpProducts == null || wpProducts.isEmpty()) continue;

                    int wpProductId = (int) wpProducts.get(0).get("id");

                    Map<WpAttributeTypeEntity, List<WpAttributeValueEntity>> byType = new LinkedHashMap<>();
                    for (WpAttributeValueEntity av : product.getAttributeValues()) {
                        byType.computeIfAbsent(av.getAttributeType(), k -> new ArrayList<>()).add(av);
                    }

                    List<Map<String, Object>> attributesList = new ArrayList<>();
                    for (Map.Entry<WpAttributeTypeEntity, List<WpAttributeValueEntity>> entry : byType.entrySet()) {
                        Integer wcTypeId = wcTypeIdBySlug.get(entry.getKey().getSlug());
                        if (wcTypeId == null) continue;

                        List<String> options = new ArrayList<>();
                        for (WpAttributeValueEntity av : entry.getValue()) {
                            String lbl = translationService.ensureValueLabel(av.getId(), site.getLanguage());
                            if (lbl != null && !lbl.isBlank()) options.add(lbl);
                        }
                        if (options.isEmpty()) continue;

                        Map<String, Object> attrMap = new HashMap<>();
                        attrMap.put("id", wcTypeId);
                        attrMap.put("visible", true);
                        attrMap.put("variation", false);
                        attrMap.put("options", options);
                        attributesList.add(attrMap);
                    }

                    if (attributesList.isEmpty()) continue;

                    restClient.patch()
                            .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/products/" + wpProductId)
                            .header("Authorization", auth)
                            .body(Map.of("attributes", attributesList))
                            .retrieve().toBodilessEntity();
                    count++;
                    log.info("ATTR PUSH → product '{}' → {}", product.getSku(), site.getUrl());
                } catch (Exception e) {
                    log.error("ATTR PUSH → product '{}' failed on {}: {}", product.getSku(), site.getUrl(), e.getMessage());
                }
            }
        }
        log.info("ATTR PUSH → done. {} product-attribute links pushed.", count);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void upsertTypeTranslation(WpAttributeTypeEntity entity, LanguageEntity lang, String label) {
        WpAttributeTypeTranslationEntity trans = entity.getTranslations().stream()
                .filter(t -> t.getLanguage().getCode().equals(lang.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    WpAttributeTypeTranslationEntity t = new WpAttributeTypeTranslationEntity();
                    t.setAttributeType(entity);
                    t.setLanguage(lang);
                    entity.getTranslations().add(t);
                    return t;
                });
        trans.setLabel(label);
        attributeTypeTranslationRepository.save(trans);
    }

    private void upsertValueTranslation(WpAttributeValueEntity entity, LanguageEntity lang, String label) {
        WpAttributeValueTranslationEntity trans = entity.getTranslations().stream()
                .filter(t -> t.getLanguage().getCode().equals(lang.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    WpAttributeValueTranslationEntity t = new WpAttributeValueTranslationEntity();
                    t.setAttributeValue(entity);
                    t.setLanguage(lang);
                    entity.getTranslations().add(t);
                    return t;
                });
        trans.setLabel(label);
        attributeValueTranslationRepository.save(trans);
    }

    private String resolveLabelForSite(List<?> translations, SiteEntity site) {
        String siteCode = site.getLanguage() != null ? site.getLanguage().getCode() : null;
        // Първо търсим езика на сайта
        if (siteCode != null) {
            for (Object t : translations) {
                if (t instanceof WpAttributeTypeTranslationEntity tt
                        && siteCode.equals(tt.getLanguage().getCode())
                        && tt.getLabel() != null && !tt.getLabel().isBlank()) return tt.getLabel();
                if (t instanceof WpAttributeValueTranslationEntity vt
                        && siteCode.equals(vt.getLanguage().getCode())
                        && vt.getLabel() != null && !vt.getLabel().isBlank()) return vt.getLabel();
            }
        }
        // Fallback — първи непразен
        for (Object t : translations) {
            if (t instanceof WpAttributeTypeTranslationEntity tt && tt.getLabel() != null && !tt.getLabel().isBlank()) return tt.getLabel();
            if (t instanceof WpAttributeValueTranslationEntity vt && vt.getLabel() != null && !vt.getLabel().isBlank()) return vt.getLabel();
        }
        return "";
    }

    private String buildAuth(SiteEntity site) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
    }

    private boolean hasCreds(SiteEntity site) {
        return site.getUrl() != null && site.getConsumerKey() != null && site.getConsumerSecret() != null;
    }
}
