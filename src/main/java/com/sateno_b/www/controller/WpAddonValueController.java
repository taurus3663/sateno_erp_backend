package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.WpAddonValueDto;
import com.sateno_b.www.model.entity.WpAddonValueEntity;
import com.sateno_b.www.model.entity.WpAddonValueTranslationEntity;
import com.sateno_b.www.model.repository.LanguageRepository;
import com.sateno_b.www.model.repository.WpAddonValueRepository;
import com.sateno_b.www.model.repository.WpAddonValueTranslationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/wp_addon_value")
@RequiredArgsConstructor
public class WpAddonValueController {

    private final WpAddonValueRepository wpAddonValueRepository;
    private final LanguageRepository languageRepository;
    private final WpAddonValueTranslationRepository wpAddonValueTranslationRepository;



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

    @PostMapping("/save")
    @Transactional
    public ResponseEntity<?> save(@RequestBody WpAddonValueDto dto) {
        // 1. Намираме съществуващата стойност или създаваме нова
        WpAddonValueEntity entity = (dto.getId() != null)
                ? wpAddonValueRepository.findById(dto.getId()).orElse(new WpAddonValueEntity())
                : new WpAddonValueEntity();

        // 2. Обновяваме slug, ако е необходимо
        if (dto.getSlug() != null && !dto.getSlug().isEmpty()) {
            entity.setSlug(dto.getSlug());
        }

        // Записваме основната субектност, за да имаме ID (ако е нова)
        WpAddonValueEntity savedEntity = wpAddonValueRepository.save(entity);

        // 3. Обработка на преводите от Map<String, Object>
        if (dto.getTranslations() != null) {
            dto.getTranslations().forEach((langCode, value) -> {
                // Извличаме label от обекта (в Angular е {label: "..."})
                String label = "";
                if (value instanceof Map) {
                    Map<?, ?> valMap = (Map<?, ?>) value;
                    label = valMap.get("label") != null ? valMap.get("label").toString() : "";
                }

                final String finalLabel = label;

                // Намираме съществуващ превод за този език или създаваме нов
                WpAddonValueTranslationEntity translation = savedEntity.getTranslations().stream()
                        .filter(t -> t.getLanguage().getCode().equals(langCode))
                        .findFirst()
                        .orElseGet(() -> {
                            WpAddonValueTranslationEntity t = new WpAddonValueTranslationEntity();
                            t.setAddonValue(savedEntity);
                            // Намираме езика в базата по код (bg, en...)
                            t.setLanguage(languageRepository.findByCode(langCode));
                            savedEntity.getTranslations().add(t);
                            return t;
                        });

                translation.setLabel(finalLabel);
                wpAddonValueTranslationRepository.save(translation);
            });
        }

        return ResponseEntity.ok(savedEntity.getId());
    }
}
