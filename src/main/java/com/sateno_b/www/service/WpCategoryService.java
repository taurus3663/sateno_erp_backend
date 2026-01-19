package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.CategoryNodeDto;
import com.sateno_b.www.model.dto.WpCategoryResponseDto;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpCategoryRepository;
import com.sateno_b.www.model.repository.WpCategorySiteMappingRepository;
import com.sateno_b.www.model.repository.WpCategoryTranslationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WpCategoryService {

    private final SiteRepository siteRepository;
    private final RestClient restClient;
    private final WpCategoryRepository wpCategoryRepository;
    private final WpCategoryTranslationRepository wpCategoryTranslationRepository;
    // Трябва да инжектираш и Mapping репозиторито
    private final WpCategorySiteMappingRepository wpCategorySiteMappingRepository;

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
        // 1. Взимаме децата директно по ID-то на родителя
        List<WpCategoryEntity> children = wpCategoryRepository.findByParentId(parentId);

        return children.stream().map(category -> {
            CategoryNodeDto node = new CategoryNodeDto();

            // 2. Взимаме името (защитаваме се от празен списък с преводи)
            String name = category.getTranslations().stream()
                    .findFirst()
                    .map(WpCategoryTranslationEntity::getName)
                    .orElse("No Name");

            // 3. Подготвяме данните за реда в таблицата
            node.setData(new CategoryNodeDto.NodeData(
                    category.getId(),
                    name,
                    category.getSlug()
            ));

            // 4. Проверка за стрелката (Leaf флаг)
            // Ако има деца, leaf е false (показва стрелката ">")
            boolean hasChildren = wpCategoryRepository.existsByParentId(category.getId());
            node.setLeaf(!hasChildren);

            return node;
        }).toList();
    }
}