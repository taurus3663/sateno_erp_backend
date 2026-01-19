package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CategoryNodeDto;
import com.sateno_b.www.model.entity.WpCategoryEntity;
import com.sateno_b.www.model.entity.WpCategorySiteMappingEntity;
import com.sateno_b.www.model.repository.WpCategoryRepository;
import com.sateno_b.www.model.repository.WpCategorySiteMappingRepository;
import com.sateno_b.www.model.repository.WpCategoryTranslationRepository;
import com.sateno_b.www.service.WpCategoryService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public Page<CategoryNodeDto> getRootNodes(Long siteId, Pageable pageable) {
        Page<WpCategoryEntity> roots = wpCategoryRepository.findByParentIsNull(pageable);

        return roots.map(category -> {
            CategoryNodeDto node = new CategoryNodeDto();

            // Взимаме името от преводите (обикновено първия или по език)
            String name = category.getTranslations().isEmpty() ? "No Name"
                    : category.getTranslations().get(0).getName();

            // Взимаме WordPress ID от мапинга за този сайт
            Long wpId = wpCategorySiteMappingRepository.findByWpCategoryAndSiteId(category, siteId)
                    .map(WpCategorySiteMappingEntity::getWpId)
                    .orElse(null);

            node.setData(new CategoryNodeDto.NodeData(
                    category.getId(),
                    name,
                    category.getSlug()
            ));

            // Проверяваме дали категорията е "листо" (няма деца)
            node.setLeaf(!wpCategoryRepository.existsByParentId(category.getId()));

            return node;
        });
    }

    @GetMapping("/find/{parentId}")
    public List<CategoryNodeDto> getChildren(
            @PathVariable Long parentId) {
        // parentId тук ще бъде 1, 2, 3... (твоето локално ID)
        return wpCategoryService.getChildrenNodes(parentId);
    }

}
