package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/wp_addon")
@RequiredArgsConstructor
public class WpAddonController {

    private final WpAddonRepository wpAddonRepository;
    private final WpAddonValueRepository wpAddonValueRepository;
    private final WpAddonValueTranslationRepository wpAddonValueTranslationRepository;
    private final LanguageRepository languageRepository;
    private final WpAddonTranslationRepository wpAddonTranslationRepository;

    @GetMapping("/list")
    public Page<WpAddonResponseDto> list(Pageable pageable) {

        return wpAddonRepository.findAll(pageable).map(entity -> {
            WpAddonResponseDto wpAddonResponseDto = new WpAddonResponseDto();
            wpAddonResponseDto.setId(entity.getId());
            wpAddonResponseDto.setSlug(entity.getSlug());

            // Превръщаме списъка с преводи в Map за по-лесно ползване в Angular
            Map<String, String> transMap = entity.getTranslations().stream()
                    .collect(Collectors.toMap(
                            t -> t.getLanguage().getCode(),
                            WpAddonTranslationEntity::getName
                    ));
            wpAddonResponseDto.setTranslations(transMap);
            String namesPipeline = String.join(" | ", transMap.values());
            wpAddonResponseDto.setNames(namesPipeline);

            return wpAddonResponseDto;
        });


    }

    @PostMapping("/save")
    @Transactional
    public ResponseEntity<?> save(@RequestBody WpAddonSaveDto dto) {
        // 1. Намираме или създаваме нова група
        WpAddonEntity entity = (dto.getId() != null)
                ? wpAddonRepository.findById(dto.getId()).orElse(new WpAddonEntity())
                : new WpAddonEntity();

        // Генерираме slug само ако липсва (за нови записи)
        if (entity.getSlug() == null || entity.getSlug().isBlank()) {
            entity.setSlug(generateSlug(dto.getName()));
        }

        // 2. ManyToMany връзката - Hibernate автоматично ще обслужи Join таблицата
        if (dto.getValueIds() != null) {
            List<WpAddonValueEntity> selectedValues = wpAddonValueRepository.findAllById(dto.getValueIds());
            entity.setValues(selectedValues);
        }

        // Записваме основния обект
        WpAddonEntity savedEntity = wpAddonRepository.save(entity);

        // 3. Обновяваме превода на името на групата
        if (dto.getName() != null && dto.getLangId() != null) {
            WpAddonTranslationEntity translation = savedEntity.getTranslations().stream()
                    .filter(t -> t.getLanguage().getId().equals(dto.getLangId()))
                    .findFirst()
                    .orElseGet(() -> {
                        WpAddonTranslationEntity t = new WpAddonTranslationEntity();
                        t.setGroup(savedEntity);
                        t.setLanguage(languageRepository.getReferenceById(dto.getLangId()));
                        // Добавяме го в списъка на обекта за консистентност
                        savedEntity.getTranslations().add(t);
                        return t;
                    });

            translation.setName(dto.getName());
            wpAddonTranslationRepository.save(translation);
        }

        return ResponseEntity.ok(savedEntity.getId());
    }


    @GetMapping("/{id}")
    public WpAddonDetailDto getDetail(@PathVariable Long id) {
        WpAddonEntity entity = wpAddonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Addon group not found"));

        WpAddonDetailDto dto = new WpAddonDetailDto();
        dto.setId(entity.getId());
        dto.setSlug(entity.getSlug());

        // 1. Мапваме избраните стойности (тези, които са в групата)
        dto.setSelectedValues(entity.getValues().stream()
                .map(this::mapToValueDto).collect(Collectors.toList()));

        // 2. Взимаме ВСИЧКИ стойности и филтрираме тези, които НЕ са в групата (Available)
        List<Long> selectedIds = dto.getSelectedValues().stream().map(WpAddonValueDto::getId).toList();
        dto.setAvailableValues(wpAddonValueRepository.findAll().stream()
                .filter(v -> !selectedIds.contains(v.getId()))
                .map(this::mapToValueDto).collect(Collectors.toList()));

        return dto;
    }


    @PostMapping("/save/value")
    @Transactional
    public WpAddonValueDto saveValue(@RequestBody Map<String, Object> payload) {
        String label = (String) payload.get("label");
        Long langId = Long.valueOf(payload.get("langId").toString());

        // Създаваме стойността
        WpAddonValueEntity value = new WpAddonValueEntity();
        // Тук добави транслитерация или просто генерирай slug
        value.setSlug(generateSlug(label));

        // Първо записваме основната стойност
        WpAddonValueEntity savedValue = wpAddonValueRepository.save(value);

        // Създаваме превода
        WpAddonValueTranslationEntity translation = new WpAddonValueTranslationEntity();
        translation.setLabel(label);
        translation.setAddonValue(savedValue);
        translation.setLanguage(languageRepository.getReferenceById(langId));

        // Записваме превода
        wpAddonValueTranslationRepository.save(translation);

        // КЛЮЧЪТ КЪМ РЕШЕНИЕТО:
        // Тъй като Hibernate не пълни автоматично List<Translations> в текущата сесия,
        // трябва ръчно да инициализираме списъка и да добавим записа, за да го види мапъра.
        if (savedValue.getTranslations() == null) {
            savedValue.setTranslations(new ArrayList<>());
        }
        savedValue.getTranslations().add(translation);

        // Сега mapToValueDto ще види превода и ще генерира JSON с "translations": {...}
        return mapToValueDto(savedValue);
    }

    @GetMapping("/list/values")
    public List<WpAddonValueDto> getAllValuesForPickList() {
        // Взимаме всички стойности от базата
        return wpAddonValueRepository.findAll().stream()
                .map(this::mapToValueDto) // Използваме същия мапър, който написахме
                .collect(Collectors.toList());
    }

    private WpAddonValueDto mapToValueDto(WpAddonValueEntity entity) {
        WpAddonValueDto dto = new WpAddonValueDto();
        dto.setId(entity.getId());
        dto.setSlug(entity.getSlug());

        // Превръщаме List<TranslationEntity> в Map<String, Map<String, String>>
        // Резултат: { "bg": { "label": "Червен" }, "en": { "label": "Red" } }
        Map<String, Object> transMap = entity.getTranslations().stream()
                .collect(Collectors.toMap(
                        t -> t.getLanguage().getCode(), // Ключ: "bg", "en"
                        t -> Map.of("label", t.getLabel()) // Стойност: { "label": "..." }
                ));

        dto.setTranslations(transMap);
        return dto;
    }

    // Помощен метод за slug, за да не е на кирилица
    private String generateSlug(String label) {
        Map<Character, String> cyrillicToLatin = new HashMap<>();
        cyrillicToLatin.put('а', "a"); cyrillicToLatin.put('б', "b"); cyrillicToLatin.put('в', "v");
        cyrillicToLatin.put('г', "g"); cyrillicToLatin.put('д', "d"); cyrillicToLatin.put('е', "e");
        cyrillicToLatin.put('ж', "zh"); cyrillicToLatin.put('з', "z"); cyrillicToLatin.put('и', "i");
        cyrillicToLatin.put('й', "y"); cyrillicToLatin.put('к', "k"); cyrillicToLatin.put('л', "l");
        cyrillicToLatin.put('м', "m"); cyrillicToLatin.put('н', "n"); cyrillicToLatin.put('о', "o");
        cyrillicToLatin.put('п', "p"); cyrillicToLatin.put('р', "r"); cyrillicToLatin.put('с', "s");
        cyrillicToLatin.put('т', "t"); cyrillicToLatin.put('у', "u"); cyrillicToLatin.put('ф', "f");
        cyrillicToLatin.put('х', "h"); cyrillicToLatin.put('ц', "ts"); cyrillicToLatin.put('ч', "ch");
        cyrillicToLatin.put('ш', "sh"); cyrillicToLatin.put('щ', "sht"); cyrillicToLatin.put('ъ', "u");
        cyrillicToLatin.put('ь', "y"); cyrillicToLatin.put('ю', "yu"); cyrillicToLatin.put('я', "ya");

        StringBuilder slug = new StringBuilder();
        String input = label.toLowerCase().trim();

        for (char c : input.toCharArray()) {
            if (cyrillicToLatin.containsKey(c)) {
                slug.append(cyrillicToLatin.get(c));
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                slug.append(c);
            } else if (c == ' ') {
                slug.append("-");
            }
        }

        String result = slug.toString().replaceAll("-+", "-");
        return result.isEmpty() ? "val-" + System.currentTimeMillis() : result;
    }

}
