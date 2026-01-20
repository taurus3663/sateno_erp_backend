package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CategoryNodeDto;
import com.sateno_b.www.model.dto.WpCategoryTranslationRequest;
import com.sateno_b.www.model.entity.WpCategoryEntity;
import com.sateno_b.www.model.entity.WpCategorySiteMappingEntity;
import com.sateno_b.www.model.entity.WpCategoryTranslationEntity;
import com.sateno_b.www.model.repository.WpCategoryRepository;
import com.sateno_b.www.model.repository.WpCategorySiteMappingRepository;
import com.sateno_b.www.model.repository.WpCategoryTranslationRepository;
import com.sateno_b.www.service.WpCategoryService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    @PostMapping("/sync")
    public void syncWpCategory(@RequestParam Long siteId) {
                wpCategoryService.syncCategoriesToDatabase(siteId);
    }

    @GetMapping("/list")
    // В WpCategoryService
    public Page<CategoryNodeDto> getRootNodes(Pageable pageable) {
        Page<WpCategoryEntity> roots = wpCategoryRepository.findByParentIsNull(pageable);

        List<Long> ids = roots.getContent().stream().map(WpCategoryEntity::getId).toList();
        Set<Long> parentIds = ids.isEmpty() ? Collections.emptySet() : wpCategoryRepository.findIdsWithChildren(ids);

        return roots.map(category -> {
            CategoryNodeDto node = new CategoryNodeDto();

            // ВАЖНО: Тук Hibernate ще направи втората заявка (Batch),
            // защото докосваме .getTranslations()
            String combinedNames = category.getTranslations().stream()
                    .map(WpCategoryTranslationEntity::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" | "));

            node.setData(new CategoryNodeDto.NodeData(
                    category.getId(),
                    combinedNames.isEmpty() ? "No Name" : combinedNames,
                    category.getSlug()
            ));

            node.setLeaf(!parentIds.contains(category.getId()));
            return node;
        });
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
        wpCategoryService.updateCategoryTranslation(request);
        return ResponseEntity.ok().build();
    }

}
