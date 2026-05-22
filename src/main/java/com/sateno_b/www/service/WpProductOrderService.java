package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.WpProductDto;
import com.sateno_b.www.model.dto.WpProductOrderDTO;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.WpCategoryRepository;
import com.sateno_b.www.model.repository.WpProductOrderRepository;
import com.sateno_b.www.model.repository.WpProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Slf4j
public class WpProductOrderService {

    private final WpProductOrderRepository wpProductOrderRepository;
    private final WpCategoryRepository wpCategoryRepository;
    private final WpProductRepository wpProductRepository;
    private final WpProductOrderAsyncService asyncService;

    public Page<WpProductOrderDTO> getAll(Pageable pageable, Map<String, String> params) {
        String category_id = params.get("category_id");
        if(category_id == null || category_id.isEmpty()) {
            return Page.empty(pageable);
        }

        // 1. Взимаме страницата с поръчките
        Page<WpProductOrderEntity> wpPage = wpProductOrderRepository.findAllByCategoryId(Long.valueOf(category_id), pageable);

        // 2. Събираме всички уникални productIds от всички намерени обекти в тази страница
        Set<Long> allProductIds = wpPage.getContent().stream()
                .flatMap(entity -> entity.getProductIds().stream())
                .collect(Collectors.toSet());

        // 3. Зареждаме продуктите наведнъж (използвай findAllById)
        Map<Long, WpProductEntity> productMap = wpProductRepository.findAllById(allProductIds)
                .stream()
                .collect(Collectors.toMap(WpProductEntity::getId, p -> p));

        // 4. Мапваме към DTO
        return wpPage.map(entity -> {
            WpProductOrderDTO dto = new WpProductOrderDTO();
//            dto.setCategory(entity.getCategory().getId());
//            dto.setProductIds(entity.getProductIds());

            // Превръщаме списъка с Long ID-та в списък с реални продуктови обекти (или DTO-та)
            List<WpProductDto> products = entity.getProductIds().stream()
                    .map(productMap::get)
                    .filter(Objects::nonNull)
                    .map(this::convertToProductDTO) // Помощен метод за превръщане в DTO
                    .collect(Collectors.toList());

            dto.setProducts(products); // Тук сетваш реалните продукти
//            dto.setCategoryName(entity.getCategory().ge());
            return dto;
        });
    }

    private WpProductDto convertToProductDTO(WpProductEntity entity) {
        WpProductDto dto = new WpProductDto();

        // Прехвърляш само нужните полета, които ти трябват за таблицата в Angular
        dto.setId(entity.getId());
        String names = entity.getTranslations().stream()
                .map(WpProductTranslationEntity::getName)
                .collect(Collectors.joining(" | "));
        dto.setNames(names);
        dto.setSku(entity.getSku());
        dto.setStockQuantity(entity.getStockQuantity());
        List<WpProductImageEntity> pImages = entity.getImages();
        WpProductImageEntity pImage = null;

// Първо се уверяваме, че списъкът със снимки изобщо съществува и не е празен
        if (pImages != null && !pImages.isEmpty()) {
            pImage = pImages.stream()
                    // Boolean.TRUE.equals е по-безопасно от == true, защото пази от грешки, ако getIsPrimary() върне null
                    .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                    .findFirst()
                    .orElse(pImages.get(0)); // Тук вече е абсолютно безопасно да поискаме индекс 0
        }

// Записваме картинката в DTO-то, само ако реално сме намерили такава
        if (pImage != null) {
            dto.setM_image(pImage.getLocalSrc());
        }


        return dto;
    }

    public void save(WpProductOrderDTO dto) {
        System.out.println(dto.toString());

        WpProductOrderEntity order = wpProductOrderRepository.findByCategoryId(dto.getCategory())
                .orElse(new WpProductOrderEntity());

        if (order.getCategory() == null) {
            WpCategoryEntity cat = wpCategoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            order.setCategory(cat);
        }

        List<Long> currentIds = order.getProductIds();
        currentIds.clear(); // Изчистваме стария списък (без да сменяме инстанцията)
        if (dto.getProductIds() != null) {
            currentIds.addAll(dto.getProductIds()); // Добавяме новите ID-та
        }

        wpProductOrderRepository.saveAndFlush(order);
        asyncService.updateWpProductOrder(order);
    }
}
