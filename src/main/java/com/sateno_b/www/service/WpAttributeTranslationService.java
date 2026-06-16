package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WpAttributeTranslationService {

    private final WpAttributeTypeRepository attributeTypeRepository;
    private final WpAttributeTypeTranslationRepository attributeTypeTranslationRepository;
    private final WpAttributeValueRepository attributeValueRepository;
    private final WpAttributeValueTranslationRepository attributeValueTranslationRepository;
    private final LanguageRepository languageRepository;
    private final ChatGptService chatGptService;

    @Transactional
    public String ensureTypeLabel(Long typeId, LanguageEntity targetLang) {
        WpAttributeTypeEntity type = attributeTypeRepository.findById(typeId).orElseThrow();
        String existing = type.getTranslations().stream()
                .filter(t -> t.getLanguage().getCode().equals(targetLang.getCode())
                        && t.getLabel() != null && !t.getLabel().isBlank())
                .map(WpAttributeTypeTranslationEntity::getLabel)
                .findFirst().orElse(null);
        if (existing != null) return existing;

        WpAttributeTypeTranslationEntity source = type.getTranslations().stream()
                .filter(t -> t.getLabel() != null && !t.getLabel().isBlank())
                .findFirst().orElse(null);
        if (source == null) return "";

        String translated = translate(source.getLabel(), source.getLanguage().getCode(), targetLang.getName());
        if (translated != null) {
            upsertTypeTranslation(type, targetLang, translated);
            return translated;
        }
        return source.getLabel();
    }

    @Transactional
    public String ensureValueLabel(Long valueId, LanguageEntity targetLang) {
        WpAttributeValueEntity value = attributeValueRepository.findById(valueId).orElseThrow();
        String existing = value.getTranslations().stream()
                .filter(t -> t.getLanguage().getCode().equals(targetLang.getCode())
                        && t.getLabel() != null && !t.getLabel().isBlank())
                .map(WpAttributeValueTranslationEntity::getLabel)
                .findFirst().orElse(null);
        if (existing != null) return existing;

        WpAttributeValueTranslationEntity source = value.getTranslations().stream()
                .filter(t -> t.getLabel() != null && !t.getLabel().isBlank())
                .findFirst().orElse(null);
        if (source == null) return "";

        String translated = translate(source.getLabel(), source.getLanguage().getCode(), targetLang.getName());
        if (translated != null) {
            upsertValueTranslation(value, targetLang, translated);
            return translated;
        }
        return source.getLabel();
    }

    @Async
    @Transactional
    public void translateAll(String sourceLangCode) {
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        List<LanguageEntity> allLanguages = languageRepository.findAll();
        List<LanguageEntity> targetLanguages = allLanguages.stream()
                .filter(l -> !l.getCode().equals(sourceLangCode))
                .toList();

        if (targetLanguages.isEmpty()) return;

        log.info("ATTR TRANSLATE → languages: {}",
                targetLanguages.stream().map(LanguageEntity::getCode).toList());

        for (WpAttributeTypeEntity type : attributeTypeRepository.findAll()) {
            String sourceLabel = type.getTranslations().stream()
                    .filter(t -> sourceLangCode.equals(t.getLanguage().getCode()))
                    .map(WpAttributeTypeTranslationEntity::getLabel)
                    .findFirst().orElse(null);
            if (sourceLabel == null || sourceLabel.isBlank()) continue;

            for (LanguageEntity lang : targetLanguages) {
                boolean hasLabel = type.getTranslations().stream()
                        .anyMatch(t -> t.getLanguage().getCode().equals(lang.getCode())
                                && t.getLabel() != null && !t.getLabel().isBlank());
                if (hasLabel) continue;

                String translated = translate(sourceLabel, sourceLangCode, lang.getName());
                if (translated != null) upsertTypeTranslation(type, lang, translated);
            }
        }

        for (WpAttributeValueEntity value : attributeValueRepository.findAll()) {
            String sourceLabel = value.getTranslations().stream()
                    .filter(t -> sourceLangCode.equals(t.getLanguage().getCode()))
                    .map(WpAttributeValueTranslationEntity::getLabel)
                    .findFirst().orElse(null);
            if (sourceLabel == null || sourceLabel.isBlank()) continue;

            for (LanguageEntity lang : targetLanguages) {
                boolean hasLabel = value.getTranslations().stream()
                        .anyMatch(t -> t.getLanguage().getCode().equals(lang.getCode())
                                && t.getLabel() != null && !t.getLabel().isBlank());
                if (hasLabel) continue;

                String translated = translate(sourceLabel, sourceLangCode, lang.getName());
                if (translated != null) upsertValueTranslation(value, lang, translated);
            }
        }

        log.info("ATTR TRANSLATE → completed");
    }

    private String translate(String text, String sourceLangCode, String targetLanguageName) {
        try {
            String result = chatGptService.translateText(
                    text,
                    String.format("Translate this product attribute from %s to %s. Return only the translated text, nothing else.",
                            sourceLangCode, targetLanguageName)
            );
            return (result != null && !result.isBlank()) ? result.trim() : null;
        } catch (Exception e) {
            log.warn("Translation failed for '{}': {}", text, e.getMessage());
            return null;
        }
    }

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
}
