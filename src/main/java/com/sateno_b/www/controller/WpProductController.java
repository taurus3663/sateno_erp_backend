package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.WpAddonRepository;
import com.sateno_b.www.model.repository.WpProductAddonConfigRepository;
import com.sateno_b.www.model.repository.WpProductRepository;
import com.sateno_b.www.model.repository.WpProductTranslationRepository;
import com.sateno_b.www.service.WpProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/wp_product")
public class WpProductController {

    private final WpProductRepository wpProductRepository;
    private final ModelMapper modelMapper;
    private final WpProductService wpProductService;
    private final WpProductTranslationRepository wpProductTranslationRepository;
    private final WpProductAddonConfigRepository wpProductAddonConfigRepository;
    private final WpAddonRepository wpAddonRepository;


    @PostMapping("/save")
    public ResponseEntity<WpProductDto> saveProduct(@RequestBody WpProductDto productDto) {

        try {
            WpProductDto savedDto = wpProductService.saveProductWithImages(productDto);
            return ResponseEntity.ok(savedDto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Page<WpProductDto>> getWpProducts(Pageable pageable) {

        Page<WpProductEntity> dtoPage =  wpProductRepository.findAll(pageable);
        Page<WpProductDto> dtos = dtoPage.map(entity -> {
            WpProductDto wpProductDto = new WpProductDto();
            wpProductDto.setWeight(entity.getWeight());
            wpProductDto.setStockQuantity(entity.getStockQuantity());
            wpProductDto.setId(entity.getId());
            wpProductDto.setUnit(entity.getUnit());

            String names = entity.getTranslations()
                    .stream()
                    .map(WpProductTranslationEntity::getName)
                    .collect(Collectors.joining(" | "));
            wpProductDto.setNames(names);
            wpProductDto.setStatus(entity.getStatus());
//            wpProductTranslationRepository.findA

            // 2. Безопасна снимка
            if (entity.getImages() != null && !entity.getImages().isEmpty()) {
                // Взимаме първата снимка от списъка на Entity-то
                String localPath = entity.getImages().get(0).getLocalSrc(); // Резултат: /media/products/1/image.jpg

                // Базовият URL на твоя сървър
                //                    String baseUrl = "http://192.168.31.232:9494";
                // Тъй като localPath вече започва с /media, просто ги съединяваме
                //                    String fullUrl = localPath;
                // Записваме пълния URL в DTO-то за Angular
                wpProductDto.setM_image(localPath);
            } else {
                wpProductDto.setM_image(null);
            }

            return wpProductDto;
        });

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<WpProductDto> getWpProduct(@PathVariable Long id) {
        // 1. Взимаме продукта от базата
        WpProductEntity entity = wpProductRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // 2. Създаваме DTO-то
        WpProductDto dto = new WpProductDto();
        dto.setId(entity.getId());
        dto.setStockQuantity(entity.getStockQuantity());
        dto.setWeight(entity.getWeight());
        dto.setBrand(modelMapper.map(entity.getBrand(), WpBrandDto.class)); // Тук ще върне обекта на бранда
        dto.setStatus(entity.getStatus());
        dto.setUnit(entity.getUnit());
        dto.setCategories(entity.getCategories()
                .stream()
                .map(wpCategoryEntity ->
                        modelMapper.map(wpCategoryEntity, WpCategoryDetailDto.class)).toList());
        dto.setTranslations(entity.getTranslations().stream().map(wpProductTranslationEntity -> modelMapper.map(wpProductTranslationEntity, WpProductTranslationDto.class)).toList());
        dto.setSiteConfig(entity.getSiteConfigs().stream().map(en -> modelMapper.map(en, WpProductSiteConfigDto.class)).collect(Collectors.toList()));


        //IMAGES
        if (entity.getImages() != null) {
            dto.setImages(entity.getImages().stream().map(img -> {
                WpProductImageDto imgDto = new WpProductImageDto();
                imgDto.setId(img.getId());
                // Добавяме базовия URL към локалния път
                imgDto.setLocalSrc(img.getLocalSrc());
                imgDto.setTemp(false); // Заредени от БД не са временни
                return imgDto;
            }).toList());
        }

        // ADDON CONFIG
        if (entity.getAddonConfig() != null) {
            dto.setAddonConfigs(entity.getAddonConfig().stream()
                    .map(config -> {
                        WpProductAddonConfigDto configDto = new WpProductAddonConfigDto();
                        configDto.setId(config.getId());
                        configDto.setPriceModifier(config.getPriceModifier());

                        // Мапваме целия Сайт
                        configDto.setSite(modelMapper.map(config.getSite(), SiteDto.class));

                        // Мапваме цялото AddonValue (за да имаш преводите в Angular)
                        configDto.setAddonValue(modelMapper.map(config.getAddonValue(), WpAddonValueDto2.class));

                        return configDto;
                    }).toList()); // Не забравяй .toList() накрая
        }

        return ResponseEntity.ok(dto);
    }


    @PostMapping("/sync/{siteId}")
    public boolean syncWpProducts(@PathVariable Long siteId) {
        wpProductService.syncProductsToDB(siteId);
        return true;
    }

    @PostMapping("/upload_temp")
    public ResponseEntity<Map<String, String>> uploadTemp(@RequestParam("file") MultipartFile file) {
        try {
            String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String tempName = "temp_" + UUID.randomUUID().toString() + "." + extension;

            // Използвай същия базов път като в WebConfig
            String rootPath = System.getProperty("user.home") + "/uploads/sateno_pim/";
            Path tempDir = Paths.get(rootPath, "temp"); // Слагаме ги в /uploads/sateno_pim/temp/

            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            Files.copy(file.getInputStream(), tempDir.resolve(tempName), StandardCopyOption.REPLACE_EXISTING);

            Map<String, String> response = new HashMap<>();
            response.put("fileName", tempName);
            // Тук URL-ът ще бъде правилно разпознат от WebConfig, защото започва с /media/
            response.put("url", "/media/temp/" + tempName);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/get_addon_values/{id}")
    public WpAddonDetailDto getAddonValues(@PathVariable Long id) {
        WpAddonEntity entity = wpAddonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Addon group not found"));

        WpAddonDetailDto dto = new WpAddonDetailDto();
        dto.setId(entity.getId());
        dto.setSlug(entity.getSlug());

        dto.setSelectedValues(entity.getValues().stream()
                .map(this::mapToValueDto).collect(Collectors.toList()));

        return dto;
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
}
