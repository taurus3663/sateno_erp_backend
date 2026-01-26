package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.WpProductDto;
import com.sateno_b.www.model.dto.WpProductTranslationDto;
import com.sateno_b.www.model.entity.WpProductEntity;
import com.sateno_b.www.model.entity.WpProductImageEntity;
import com.sateno_b.www.model.entity.WpProductTranslationEntity;
import com.sateno_b.www.model.repository.WpProductRepository;
import com.sateno_b.www.model.repository.WpProductTranslationRepository;
import com.sateno_b.www.service.WpProductService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wp_product")
public class WpProductController {

    private final WpProductRepository wpProductRepository;
    private final ModelMapper modelMapper;
    private final WpProductService wpProductService;
    private final WpProductTranslationRepository wpProductTranslationRepository;

    @GetMapping("/list")
    public ResponseEntity<Page<WpProductDto>> getWpProducts(Pageable pageable) {

        Page<WpProductEntity> dtoPage =  wpProductRepository.findAll(pageable);
        Page<WpProductDto> dtos = dtoPage.map(entity -> {
            WpProductDto wpProductDto = new WpProductDto();
            wpProductDto.setWeight(entity.getWeight());
            wpProductDto.setStockQuantity(entity.getStockQuantity());
            wpProductDto.setId(entity.getId());

            String names = entity.getTranslations()
                    .stream()
                    .map(WpProductTranslationEntity::getName)
                    .collect(Collectors.joining(" | "));
            wpProductDto.setNames(names);
            wpProductDto.setStatus_p(entity.getStatus());
//            wpProductTranslationRepository.findA

            // 2. Безопасна снимка
            if (entity.getImages() != null && !entity.getImages().isEmpty()) {
                String fullPath = entity.getImages().get(0).getLocalSrc();

                if (fullPath != null) {
                    // Нормализираме за всеки случай
                    fullPath = fullPath.replace("\\", "/");

                    // Вместо да търсим "/media", можем да търсим "/uploads/",
                    // защото знаем, че снимките са там
                    int uploadsIndex = fullPath.indexOf("/uploads/sateno_pim");
                    if (uploadsIndex != -1) {
                        // Превръщаме физическия път в URL път за Angular
                        // Резултат: /media/products/1/image.jpg
                        String webPath = "/media" + fullPath.substring(uploadsIndex + "/uploads/sateno_pim".length());
                        wpProductDto.setM_image(webPath);
                    } else {
                        wpProductDto.setM_image(null);
                    }
                }
            } else {
                wpProductDto.setM_image(null);
            }

            return wpProductDto;
        });

        return ResponseEntity.ok(dtos);
    }


    @PostMapping("/sync/{siteId}")
    public boolean syncWpProducts(@PathVariable Long siteId) {
        wpProductService.syncProductsToDB(siteId);
        return true;
    }
}
