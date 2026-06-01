package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CategoryNodeDto;
import com.sateno_b.www.model.dto.WpCategoryDetailDto;
import com.sateno_b.www.model.dto.WpCategoryTranslationRequest;
import com.sateno_b.www.model.entity.WpCategoryEntity;
import com.sateno_b.www.model.entity.WpCategorySiteMappingEntity;
import com.sateno_b.www.model.entity.WpCategoryTranslationEntity;
import com.sateno_b.www.model.repository.WpCategoryRepository;
import com.sateno_b.www.model.repository.WpCategorySiteMappingRepository;
import com.sateno_b.www.model.repository.WpCategoryTranslationRepository;
import com.sateno_b.www.service.WpCategoryAsyncService;
import com.sateno_b.www.service.WpCategoryService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/wp_category")
@RequiredArgsConstructor
public class WpCategoryController {

    private final WpCategoryRepository wpCategoryRepository;
    private final WpCategoryTranslationRepository wpCategoryTranslationRepository;
    private final WpCategorySiteMappingRepository wpCategorySiteMappingRepository;
    private final ModelMapper modelMapper;
    private final WpCategoryService wpCategoryService;
    private final WpCategoryAsyncService  wpCategoryAsyncService;

//    @PostMapping("/sync/{siteId}")
//    public boolean syncWpCategory(@PathVariable Long siteId) {
//                wpCategoryService.syncCategoriesToDatabase(siteId);
//                return true;
//    }


    @GetMapping("/list")
    public Page<CategoryNodeDto> getRootNodes(Pageable pageable) {
        Page<WpCategoryEntity> roots = wpCategoryRepository.findByParentIsNull(pageable);

        List<Long> ids = roots.getContent().stream().map(WpCategoryEntity::getId).toList();
        Set<Long> parentIds = ids.isEmpty() ? Collections.emptySet() : wpCategoryRepository.findIdsWithChildren(ids);

        return roots.map(category -> {
            CategoryNodeDto node = new CategoryNodeDto();

            String combinedNames = category.getTranslations().stream()
                    .map(WpCategoryTranslationEntity::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" | "));

            // Подаваме 5 параметъра: id, name, slug, parentId, parentName
            CategoryNodeDto.NodeData data = new CategoryNodeDto.NodeData(
                    category.getId(),
                    combinedNames.isEmpty() ? "No Name" : combinedNames,
                    category.getSlug(),
                    null, // parentId (няма, защото е root)
                    null  // parentName (няма, защото е root)
            );

            node.setData(data);
            node.setLeaf(!parentIds.contains(category.getId()));
            return node;
        });
    }

    @GetMapping("/detail/{id}")
    public WpCategoryDetailDto getWpCategoryDetail(@PathVariable Long id) {
        WpCategoryEntity category = wpCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        WpCategoryDetailDto dto = modelMapper.map(category, WpCategoryDetailDto.class);

        // 1. Сетваме комбинираното име за самата категория (BG | EN)
        String combinedNames = category.getTranslations().stream()
                .map(WpCategoryTranslationEntity::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" | "));
        dto.setName(combinedNames.isEmpty() ? "No Name" : combinedNames);

        // 2. Логика за Родителя
        if (category.getParent() != null) {
            WpCategoryEntity parent = category.getParent();

            // Сетваме ID-то на родителя
            dto.setParentId(parent.getId());

            // Комбинираме имената на родителя (BG | EN), за да се виждат в инпута в Angular
            String parentCombinedNames = parent.getTranslations().stream()
                    .map(WpCategoryTranslationEntity::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" | "));

            dto.setParentName(parentCombinedNames.isEmpty() ? parent.getSlug() : parentCombinedNames);
        } else {
            dto.setParentId(null);
            dto.setParentName("Основна категория");
        }

        return dto;
    }

    @GetMapping("/find/{parentId}")
    public List<CategoryNodeDto> getChildren(
            @PathVariable Long parentId) {
        // parentId тук ще бъде 1, 2, 3... (твоето локално ID)
        return wpCategoryService.getChildrenNodes(parentId);
    }

    @GetMapping("/get/translation")
    public ResponseEntity<String> getTranslation(
            @RequestParam Long categoryId,
            @RequestParam Long languageId) {
        String name = wpCategoryService.getTranslationName(categoryId, languageId);
        return ResponseEntity.ok(name);
    }

    @PostMapping("/update/translation")
    public ResponseEntity<Void> updateTranslation(@RequestBody WpCategoryTranslationRequest request) {
        wpCategoryService.saveOrUpdateCategory(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/all")
    public List<CategoryNodeDto> getAllNodes() {
        // Връщаме абсолютно всички категории в плосък списък
        return wpCategoryService.getAllNodesFlat();
    }

//    @PostMapping("/sync/to/{siteId}")
//    public boolean syncWpCategoryToSite(@PathVariable Long siteId) {
//        return wpCategoryAsyncService.syncWpCategoryToSite(siteId);
//    }

}
