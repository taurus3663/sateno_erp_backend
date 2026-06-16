package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.WpAttributeTypeDto;
import com.sateno_b.www.model.dto.WpAttributeValueDto;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.service.WpAttributeSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/wp_attribute")
@RequiredArgsConstructor
public class WpAttributeController {

    private final WpAttributeTypeRepository typeRepository;
    private final WpAttributeTypeTranslationRepository typeTranslationRepository;
    private final WpAttributeValueRepository valueRepository;
    private final WpAttributeValueTranslationRepository valueTranslationRepository;
    private final LanguageRepository languageRepository;
    private final WpAttributeSyncService syncService;

    // ── Types ─────────────────────────────────────────────────────────────────

    @GetMapping("/type/list")
    @Transactional(readOnly = true)
    public List<WpAttributeTypeDto> listTypes() {
        return typeRepository.findAll().stream()
                .map(this::mapType)
                .collect(Collectors.toList());
    }

    @GetMapping("/type/list-with-values")
    @Transactional(readOnly = true)
    public List<WpAttributeTypeDto> listTypesWithValues() {
        return typeRepository.findAll().stream()
                .map(type -> {
                    WpAttributeTypeDto dto = mapType(type);
                    dto.setValues(type.getValues().stream()
                            .map(this::mapValue)
                            .collect(Collectors.toList()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/type/{id}")
    @Transactional(readOnly = true)
    public WpAttributeTypeDto getType(@PathVariable Long id) {
        WpAttributeTypeEntity entity = typeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Attribute type not found: " + id));
        return mapType(entity);
    }

    @PostMapping("/type/save")
    @Transactional
    public ResponseEntity<?> saveType(@RequestBody WpAttributeTypeDto dto) {
        WpAttributeTypeEntity entity = dto.getId() != null
                ? typeRepository.findById(dto.getId()).orElse(new WpAttributeTypeEntity())
                : new WpAttributeTypeEntity();

        String slug = (dto.getSlug() != null && !dto.getSlug().isBlank())
                ? dto.getSlug()
                : generateSlug(dto.getTranslations());
        entity.setSlug(slug);
        entity.setMultipleValues(dto.isMultipleValues());
        WpAttributeTypeEntity saved = typeRepository.save(entity);

        if (dto.getTranslations() != null) {
            dto.getTranslations().forEach((langCode, value) -> {
                String label = extractLabel(value);
                if (label != null && !label.isBlank()) {
                    updateTypeTranslation(saved, langCode, label);
                }
            });
        }

        syncService.pushTypeToAllSites(saved);
        return ResponseEntity.ok(saved.getId());
    }

    @DeleteMapping("/type/{id}")
    @Transactional
    public ResponseEntity<?> deleteType(@PathVariable Long id) {
        typeRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // ── Values ────────────────────────────────────────────────────────────────

    @GetMapping("/value/by-type/{typeId}")
    @Transactional(readOnly = true)
    public List<WpAttributeValueDto> listValuesByType(@PathVariable Long typeId) {
        return valueRepository.findAllByAttributeTypeId(typeId).stream()
                .map(this::mapValue)
                .collect(Collectors.toList());
    }

    @GetMapping("/value/{id}")
    @Transactional(readOnly = true)
    public WpAttributeValueDto getValue(@PathVariable Long id) {
        WpAttributeValueEntity entity = valueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Attribute value not found: " + id));
        return mapValue(entity);
    }

    @PostMapping("/value/save")
    @Transactional
    public ResponseEntity<?> saveValue(@RequestBody WpAttributeValueDto dto) {
        WpAttributeValueEntity entity = dto.getId() != null
                ? valueRepository.findById(dto.getId()).orElse(new WpAttributeValueEntity())
                : new WpAttributeValueEntity();

        WpAttributeTypeEntity type = typeRepository.findById(dto.getTypeId())
                .orElseThrow(() -> new RuntimeException("Attribute type not found: " + dto.getTypeId()));

        entity.setAttributeType(type);
        String slug = (dto.getSlug() != null && !dto.getSlug().isBlank())
                ? dto.getSlug()
                : generateSlug(dto.getTranslations());
        entity.setSlug(slug);
        WpAttributeValueEntity saved = valueRepository.save(entity);

        if (dto.getTranslations() != null) {
            dto.getTranslations().forEach((langCode, value) -> {
                String label = extractLabel(value);
                if (label != null && !label.isBlank()) {
                    updateValueTranslation(saved, langCode, label);
                }
            });
        }

        syncService.pushValueToAllSites(saved);
        return ResponseEntity.ok(saved.getId());
    }

    @DeleteMapping("/value/{id}")
    @Transactional
    public ResponseEntity<?> deleteValue(@PathVariable Long id) {
        valueRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WpAttributeTypeDto mapType(WpAttributeTypeEntity entity) {
        WpAttributeTypeDto dto = new WpAttributeTypeDto();
        dto.setId(entity.getId());
        dto.setSlug(entity.getSlug());
        dto.setMultipleValues(entity.isMultipleValues());

        Map<String, Object> transMap = entity.getTranslations().stream()
                .filter(t -> t.getLanguage() != null)
                .collect(Collectors.toMap(
                        t -> t.getLanguage().getCode(),
                        t -> t.getLabel() != null ? t.getLabel() : "",
                        (a, b) -> a
                ));
        dto.setTranslations(transMap);

        String label = transMap.values().stream()
                .map(Object::toString).filter(s -> !s.isBlank())
                .findFirst().orElse(entity.getSlug());
        dto.setLabel(label);

        return dto;
    }

    private WpAttributeValueDto mapValue(WpAttributeValueEntity entity) {
        WpAttributeValueDto dto = new WpAttributeValueDto();
        dto.setId(entity.getId());
        dto.setSlug(entity.getSlug());
        dto.setTypeId(entity.getAttributeType() != null ? entity.getAttributeType().getId() : null);

        Map<String, Object> transMap = entity.getTranslations().stream()
                .filter(t -> t.getLanguage() != null)
                .collect(Collectors.toMap(
                        t -> t.getLanguage().getCode(),
                        t -> t.getLabel() != null ? t.getLabel() : "",
                        (a, b) -> a
                ));
        dto.setTranslations(transMap);

        String label = transMap.values().stream()
                .map(Object::toString).filter(s -> !s.isBlank())
                .findFirst().orElse(entity.getSlug());
        dto.setLabel(label);

        return dto;
    }

    private void updateTypeTranslation(WpAttributeTypeEntity entity, String langCode, String label) {
        WpAttributeTypeTranslationEntity trans = entity.getTranslations().stream()
                .filter(t -> t.getLanguage().getCode().equals(langCode))
                .findFirst()
                .orElseGet(() -> {
                    WpAttributeTypeTranslationEntity t = new WpAttributeTypeTranslationEntity();
                    t.setAttributeType(entity);
                    t.setLanguage(languageRepository.findByCode(langCode));
                    entity.getTranslations().add(t);
                    return t;
                });
        trans.setLabel(label);
        typeTranslationRepository.save(trans);
    }

    private void updateValueTranslation(WpAttributeValueEntity entity, String langCode, String label) {
        WpAttributeValueTranslationEntity trans = entity.getTranslations().stream()
                .filter(t -> t.getLanguage().getCode().equals(langCode))
                .findFirst()
                .orElseGet(() -> {
                    WpAttributeValueTranslationEntity t = new WpAttributeValueTranslationEntity();
                    t.setAttributeValue(entity);
                    t.setLanguage(languageRepository.findByCode(langCode));
                    entity.getTranslations().add(t);
                    return t;
                });
        trans.setLabel(label);
        valueTranslationRepository.save(trans);
    }

    private String extractLabel(Object value) {
        if (value == null) return "";
        if (value instanceof String) return (String) value;
        if (value instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) value;
            return m.containsKey("label") ? m.get("label").toString() : "";
        }
        return value.toString();
    }

    private String generateSlug(Map<String, Object> translations) {
        if (translations == null || translations.isEmpty()) return "pa_attribute";
        String label = translations.values().stream()
                .map(this::extractLabel)
                .filter(s -> s != null && !s.isBlank())
                .findFirst().orElse("attribute");
        String slugBody = transliterate(label.toLowerCase())
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return "pa_" + slugBody;
    }

    private String transliterate(String text) {
        String[][] map = {
            {"щ","sht"},{"ж","zh"},{"ц","ts"},{"ч","ch"},{"ш","sh"},{"ю","yu"},{"я","ya"},
            {"а","a"},{"б","b"},{"в","v"},{"г","g"},{"д","d"},{"е","e"},{"з","z"},
            {"и","i"},{"й","y"},{"к","k"},{"л","l"},{"м","m"},{"н","n"},{"о","o"},
            {"п","p"},{"р","r"},{"с","s"},{"т","t"},{"у","u"},{"ф","f"},{"х","h"},
            {"ъ","a"},{"ь",""},{"є","e"},{"і","i"},{"ї","yi"},{"ґ","g"}
        };
        for (String[] pair : map) text = text.replace(pair[0], pair[1]);
        return text;
    }
}
