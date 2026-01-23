package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.CategoryNodeDto;
import com.sateno_b.www.model.dto.WpCategoryDetailDto;
import com.sateno_b.www.model.dto.WpCategoryResponseDto;
import com.sateno_b.www.model.dto.WpCategoryTranslationRequest;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WpCategoryService {

    private final SiteRepository siteRepository;
    private final RestClient restClient;
    private final WpCategoryRepository wpCategoryRepository;
    private final WpCategoryTranslationRepository wpCategoryTranslationRepository;
    private final WpCategorySiteMappingRepository wpCategorySiteMappingRepository;
    private final LanguageRepository languageRepository;

    @Transactional
    public void syncCategoriesToDatabase(Long siteId) {
        SiteEntity site = siteRepository.findById(siteId).orElseThrow();
        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

        // 1. Изтегляме всички страници
        List<WpCategoryResponseDto> allCats = fetchAllCategories(site, auth);

        for (WpCategoryResponseDto dto : allCats) {
            String decodedSlug = URLDecoder.decode(dto.getSlug(), StandardCharsets.UTF_8);

            // Търсим или създаваме основната категория по Slug
            WpCategoryEntity entity = wpCategoryRepository.findBySlug(decodedSlug)
                    .orElseGet(() -> {
                        WpCategoryEntity newCat = new WpCategoryEntity();
                        newCat.setSlug(decodedSlug);
                        return wpCategoryRepository.save(newCat);
                    });

            // 2. Сетваме РОДИТЕЛЯ чрез Mapping таблицата
            if (dto.getParent() != 0) {
                // Търсим мапинга на родителя за ТОЗИ сайт
                wpCategorySiteMappingRepository.findBySiteIdAndWpId(siteId, dto.getParent())
                        .ifPresent(parentMapping -> {
                            entity.setParent(parentMapping.getWpCategory());
                        });
            } else {
                entity.setParent(null);
            }

            // Запазваме промените по йерархията
            WpCategoryEntity savedEntity = wpCategoryRepository.save(entity);

            // 3. Обновяваме Mapping-а (връзката Site <-> Category <-> WpId)
            updateMapping(savedEntity, site, dto.getId());

            // 4. Добавяме/обновяваме превода
            updateTranslation(savedEntity, dto.getName(), site.getLanguage());
        }
    }

    private void updateMapping(WpCategoryEntity category, SiteEntity site, Long wpId) {
        WpCategorySiteMappingEntity mapping = wpCategorySiteMappingRepository
                .findBySiteIdAndWpId(site.getId(), wpId)
                .orElse(new WpCategorySiteMappingEntity());

        mapping.setWpCategory(category);
        mapping.setSite(site);
        mapping.setWpId(wpId);
        wpCategorySiteMappingRepository.save(mapping);
    }

    private void updateTranslation(WpCategoryEntity entity, String name, LanguageEntity lang) {
        // Проверяваме дали вече имаме превод на този език за тази категория
        boolean exists = entity.getTranslations().stream()
                .anyMatch(t -> t.getLanguage().getId().equals(lang.getId()));

        if (!exists) {
            WpCategoryTranslationEntity translation = new WpCategoryTranslationEntity();
            translation.setName(name);
            translation.setLanguage(lang);
            // Важно: в твоето Entity използваш @JoinColumn без mappedBy,
            // но е добре да поддържаш обекта в паметта
            entity.getTranslations().add(translation);
            wpCategoryRepository.save(entity);
        }
    }

    private List<WpCategoryResponseDto> fetchAllCategories(SiteEntity site, String auth) {
        List<WpCategoryResponseDto> allCats = new ArrayList<>();
        int currentPage = 1;
        int totalPages = 1;

        do {
            var response = restClient.get()
                    .uri(site.getUrl() + "/wp-json/wc/v3/products/categories?per_page=100&page=" + currentPage + "&orderby=id&order=asc")
                    .header("Authorization", "Basic " + auth)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<WpCategoryResponseDto>>() {});

            if (response.getBody() != null) {
                allCats.addAll(response.getBody());
            }

            String totalPagesHeader = response.getHeaders().getFirst("X-WP-TotalPages");
            if (totalPagesHeader != null) {
                totalPages = Integer.parseInt(totalPagesHeader);
            }

            currentPage++;
        } while (currentPage <= totalPages);

        return allCats;
    }

    public List<CategoryNodeDto> getChildrenNodes(Long parentId) {
        // 1. Взимаме децата
        List<WpCategoryEntity> children = wpCategoryRepository.findByParentId(parentId);

        // Вземаме имената на родителя веднъж, за да не ги преизчисляваме в цикъла
        WpCategoryEntity parent = wpCategoryRepository.findById(parentId).orElse(null);
        String parentCombinedNames = (parent != null) ? parent.getTranslations().stream()
                .map(WpCategoryTranslationEntity::getName)
                .collect(Collectors.joining(" | ")) : "";
        // 2. Оптимизация за Leaf флага: Взимаме ID-тата на всички деца,
        // за да проверим с една заявка кои от тях имат свои дечица
        List<Long> childIds = children.stream().map(WpCategoryEntity::getId).toList();
        Set<Long> idsWithChildren = childIds.isEmpty() ? Collections.emptySet()
                : wpCategoryRepository.findIdsWithChildren(childIds);

        return children.stream().map(category -> {
            CategoryNodeDto node = new CategoryNodeDto();

            // 3. Комбинираме имената с " | " (двуезично/многоезично)
            // Благодарение на @BatchSize(size=30) в Entity-то, това няма да бави
            String combinedNames = category.getTranslations().stream()
                    .map(WpCategoryTranslationEntity::getName)
                    .filter(name -> name != null && !name.isEmpty())
                    .collect(Collectors.joining(" | "));

            node.setData(new CategoryNodeDto.NodeData(
                    category.getId(),
                    combinedNames.isEmpty() ? "No Name" : combinedNames,
                    category.getSlug(),
                    parentId,
                    parentCombinedNames
            ));

            // 4. Проверка за стрелката чрез Set-а от стъпка 2
            node.setLeaf(!idsWithChildren.contains(category.getId()));

            return node;
        }).toList();
    }

    public String getTranslationName(Long categoryId, Long languageId) {
        return wpCategoryTranslationRepository
                .findByWpCategoryIdAndLanguageId(categoryId, languageId)
                .map(WpCategoryTranslationEntity::getName)
                .orElse(""); // Ако няма превод, връщаме празен текст
    }

    @Transactional
    public void updateCategoryTranslation(WpCategoryTranslationRequest request) {
        // 1. Намираме основната категория (за да променим родителя)
        WpCategoryEntity category = wpCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        // 2. ОБНОВЯВАНЕ НА РОДИТЕЛЯ
        // Проверяваме дали категорията не се опитва да стане родител на самата себе си
        if (request.getParentId() != null) {
            if (request.getParentId().equals(category.getId())) {
                throw new RuntimeException("Category cannot be its own parent");
            }

            WpCategoryEntity parent = wpCategoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent category not found"));
            category.setParent(parent);
        } else {
            // Ако parentId е null (или изчистен в UI), тя става главна категория
            category.setParent(null);
        }

        // Записваме промяната в родителя
        wpCategoryRepository.save(category);

        // 3. Търсим съществуващ превод или създаваме нов
        WpCategoryTranslationEntity translation = wpCategoryTranslationRepository
                .findByWpCategoryIdAndLanguageId(request.getCategoryId(), request.getLanguageId())
                .orElseGet(() -> {
                    WpCategoryTranslationEntity t = new WpCategoryTranslationEntity();
                    t.setWpCategory(category);
                    t.setLanguage(languageRepository.getReferenceById(request.getLanguageId()));
                    return t;
                });

        // 4. Обновяваме името и записваме превода
        translation.setName(request.getName());
        wpCategoryTranslationRepository.save(translation);
    }
}