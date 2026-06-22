package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.WpAddonValueDto;
import com.sateno_b.www.model.entity.LanguageEntity;
import com.sateno_b.www.model.entity.WpAddonValueEntity;
import com.sateno_b.www.model.entity.WpAddonValueTranslationEntity;
import com.sateno_b.www.model.repository.LanguageRepository;
import com.sateno_b.www.model.repository.WpAddonValueRepository;
import com.sateno_b.www.model.repository.WpAddonValueTranslationRepository;
import com.sateno_b.www.service.ChatGptService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/wp_addon_value")
@RequiredArgsConstructor
public class WpAddonValueController {

    private final WpAddonValueRepository wpAddonValueRepository;
    private final LanguageRepository languageRepository;
    private final WpAddonValueTranslationRepository wpAddonValueTranslationRepository;
    private final ChatGptService chatGptService;


    @GetMapping("/list")
    public Page<WpAddonValueDto> list(Pageable pageable) {
        return wpAddonValueRepository.findAll(pageable).map(entity -> {
            WpAddonValueDto dto = new WpAddonValueDto();
            dto.setId(entity.getId());
            dto.setSlug(entity.getSlug());

            // 1. Мапваме преводите: { "bg": "Червен", "en": "Red" }
            Map<String, Object> transMap = entity.getTranslations().stream()
                    .collect(Collectors.toMap(
                            t -> t.getLanguage().getCode(),
                            t -> t.getLabel() != null ? t.getLabel() : ""
                    ));
            dto.setTranslations(transMap);

            // 2. Създаваме "Pipeline" стринга за таблицата
            String pipeNames = transMap.values().stream()
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining(" | "));

            dto.setNames(pipeNames);

            return dto;
        });
    }

    @GetMapping("/detail/{id}")
    @Transactional(readOnly = true)
    public WpAddonValueDto getWpAddonValueById(@PathVariable Long id) {
        // 1. Намираме обекта или хвърляме грешка
        WpAddonValueEntity entity = wpAddonValueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Addon Value not found with id: " + id));

        // 2. Инициализираме DTO
        WpAddonValueDto dto = new WpAddonValueDto();
        dto.setId(entity.getId());
        dto.setSlug(entity.getSlug());

        // 3. Мапваме преводите: { "bg": "Червен", "en": "Red" }
        // Използваме Collectors.toMap за трансформацията
        Map<String, Object> transMap = entity.getTranslations().stream()
                .filter(t -> t.getLanguage() != null)
                .collect(Collectors.toMap(
                        t -> t.getLanguage().getCode(), // Ключ: 'bg'
                        t -> t.getLabel() != null ? t.getLabel() : "", // Стойност: 'Червен'
                        (existing, replacement) -> existing
                ));
        dto.setTranslations(transMap);

        // 4. Генерираме комбинираното име за визуализация в инпутите
        String pipeNames = transMap.values().stream()
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" | "));

        dto.setNames(pipeNames.isEmpty() ? "No Label" : pipeNames);

        return dto;
    }

    @PostMapping("/save")
    @Transactional
    public ResponseEntity<?> save(@RequestBody WpAddonValueDto dto) {
        // 1. Намираме или създаваме стойността
        WpAddonValueEntity entity = (dto.getId() != null)
                ? wpAddonValueRepository.findById(dto.getId()).orElse(new WpAddonValueEntity())
                : new WpAddonValueEntity();

        entity.setSlug(dto.getSlug());
        WpAddonValueEntity savedEntity = wpAddonValueRepository.save(entity);

        // 2. Обработка на входящите преводи (от Angular)
        if (dto.getTranslations() != null) {
            dto.getTranslations().forEach((langCode, value) -> {
                String label = extractLabelText(value);

                // Записваме превода само ако не е празен
                if (label != null && !label.trim().isEmpty()) {
                    updateTranslation(savedEntity, langCode, label);
                }
            });
        }

        // 3. АВТОМАТИЗАЦИЯ: Превеждаме за езиците, които са празни (като "bg" в примера ти)
        autoFillMissingTranslations(savedEntity);

        return ResponseEntity.ok(savedEntity.getId());
    }

    private void autoFillMissingTranslations(WpAddonValueEntity entity) {
        List<LanguageEntity> allLanguages = languageRepository.findAll();

        // 1. Намираме първия превод, който НЕ Е празен (може да е 'en', може да е 'bg')
        WpAddonValueTranslationEntity source = entity.getTranslations().stream()
                .filter(t -> t.getLabel() != null && !t.getLabel().trim().isEmpty())
                .findFirst()
                .orElse(null);

        // Ако няма нито един попълнен език, няма откъде да превеждаме
        if (source == null) return;

        for (LanguageEntity lang : allLanguages) {
            // 2. Проверяваме дали за текущия език в цикъла липсва превод
            WpAddonValueTranslationEntity existingTrans = entity.getTranslations().stream()
                    .filter(t -> t.getLanguage().getCode().equals(lang.getCode()))
                    .findFirst()
                    .orElse(null);

            // Ако преводът липсва ИЛИ е празен стринг ""
            if (existingTrans == null || existingTrans.getLabel() == null || existingTrans.getLabel().trim().isEmpty()) {

                // Викаме ChatGPT да преведе от source езика към текущия lang
                String prompt = String.format(
                        "Translate this product attribute label from %s to %s. " +
                                "The source text might be a slug (with dashes), please format it as a human-readable name: '%s'",
                        source.getLanguage().getName(),
                        lang.getName(),
                        source.getLabel()
                );

                String translated = chatGptService.translateText(source.getLabel(), prompt);

                if (translated != null && !translated.isEmpty()) {
                    // Използваме метода за обновяване/създаване
                    updateTranslation(entity, lang.getCode(), translated);
                }
            }
        }
    }

    private void updateTranslation(WpAddonValueEntity entity, String langCode, String label) {
        WpAddonValueTranslationEntity translation = entity.getTranslations().stream()
                .filter(t -> t.getLanguage().getCode().equals(langCode))
                .findFirst()
                .orElseGet(() -> {
                    WpAddonValueTranslationEntity t = new WpAddonValueTranslationEntity();
                    t.setAddonValue(entity);
                    t.setLanguage(languageRepository.findByCode(langCode));
                    entity.getTranslations().add(t);
                    return t;
                });

        translation.setLabel(label);
        wpAddonValueTranslationRepository.save(translation);
    }

    private String extractLabelText(Object value) {
        if (value == null) return "";
        if (value instanceof String) return (String) value;
        if (value instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) value;
            return m.containsKey("label") ? m.get("label").toString() : "";
        }
        return value.toString();
    }

    private String extractLabel(Object value) {
        if (value instanceof Map) {
            Map<?, ?> valMap = (Map<?, ?>) value;
            return valMap.get("label") != null ? valMap.get("label").toString() : "";
        } else if (value instanceof String) {
            return (String) value;
        }
        return "";
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> delete(@RequestBody List<Long> ids) {
        ids.forEach(wpAddonValueRepository::deleteById);
        return ResponseEntity.ok().build();
    }
}
