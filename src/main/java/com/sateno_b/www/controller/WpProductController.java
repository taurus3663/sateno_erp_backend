package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.service.ChatGptService;
import com.sateno_b.www.service.WpProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
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
    private final ChatGptService chatGptService;
    private final LanguageRepository languageRepository;

    @PatchMapping("/patch")
    public ResponseEntity<?> patchProduct(@RequestBody WpProductDto wpProductDto) {
        try {
            WpProductDto p = wpProductService.patchProduct(wpProductDto);
            return ResponseEntity.ok(p);
        } catch (Exception e) {
            // Логваме грешката в конзолата на сървъра за дебъг
            e.printStackTrace();

            // Връщаме 409 Conflict със съобщението като чист текст
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("Грешка при частично обновяване: " + e.getMessage());
        }
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveProduct(@RequestBody WpProductDto productDto) {

        try {
            WpProductDto savedDto = wpProductService.saveProduct(productDto);
            return ResponseEntity.ok(savedDto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Page<WpProductDto>> getWpProducts(
            Pageable pageable,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long quantity,
            @RequestParam(required = false) Long status,
            @RequestParam(required = false) Long saleType,
            @RequestParam(required = false) String name_sku
    ) {

        Page<WpProductDto> dtoPage =  wpProductService.getAll(pageable, brand, category, quantity, status, saleType, name_sku);

        return ResponseEntity.ok(dtoPage);
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
        dto.setSku(entity.getSku());
        if(entity.getBrand() != null) {
            dto.setBrand(modelMapper.map(entity.getBrand(), WpBrandDto.class));
        }

        dto.setStatus(entity.getStatus());
        dto.setSaleType(entity.getSaleType());
//        dto.setUnit(entity.getUnit());
        dto.setCategories(entity.getCategories()
                .stream()
                .map(wpCategoryEntity ->
                        modelMapper.map(wpCategoryEntity, WpCategoryDetailDto.class)).toList());
        dto.setTranslations(entity.getTranslations().stream().map(wpProductTranslationEntity -> modelMapper.map(wpProductTranslationEntity, WpProductTranslationDto.class)).toList());
        dto.setSiteConfig(entity.getSiteConfigs().stream().map(en -> modelMapper.map(en, WpProductSiteConfigDto.class)).collect(Collectors.toList()));


        //IMAGES
        // IMAGES
        if (entity.getImages() != null) {
            dto.setImages(entity.getImages().stream().map(img -> {
                WpProductImageDto imgDto = new WpProductImageDto();
                imgDto.setId(img.getId());
                imgDto.setLocalSrc(img.getLocalSrc());
                imgDto.setTemp(false);

                // КРИТИЧНО: Мапваме и siteMappings
                if (img.getSiteMappings() != null) {
                    imgDto.setSiteMappings(img.getSiteMappings().stream().map(mapping -> {
                        // Тук използваме модел мапъра или ръчно сетваме ID-то
                        WpProductImageSiteMappingDto mappingDto = new WpProductImageSiteMappingDto();
                        mappingDto.setId(mapping.getId());
                        mappingDto.setWpMediaId(mapping.getWpMediaId());

                        // ВАЖНО: Трябва ни siteId, за да работи филтърът в Angular
                        if (mapping.getSite() != null) {
                            mappingDto.setSiteId(mapping.getSite().getId());
                        }

                        return mappingDto;
                    }).toList());
                } else {
                    imgDto.setSiteMappings(new ArrayList<>());
                }

                return imgDto;
            }).toList());
        }

        // ADDON CONFIG
//        if (entity.getAddonConfig() != null) {
//            dto.setAddonConfigs(entity.getAddonConfig().stream()
//                    .map(config -> {
//                        WpProductAddonConfigDto configDto = new WpProductAddonConfigDto();
//                        configDto.setId(config.getId());
//                        configDto.setPriceModifier(config.getPriceModifier());
//
//                        // Мапваме целия Сайт
//                        configDto.setSite(modelMapper.map(config.getSite(), SiteDto.class));
//
//                        // Мапваме цялото AddonValue (за да имаш преводите в Angular)
//                        configDto.setAddonValue(modelMapper.map(config.getAddonValue(), WpAddonValueDto2.class));
//
//                        return configDto;
//                    }).toList()); // Не забравяй .toList() накрая
//        }
        if (entity.getAddonConfig() != null) {
            dto.setAddonConfigs(entity.getAddonConfig().stream()
                    .map(config -> {
                        WpProductAddonConfigDto configDto = new WpProductAddonConfigDto();
                        configDto.setId(config.getId());
                        configDto.setPriceModifier(config.getPriceModifier());

                        // Мапваме стойността на адона (с нейните преводи за всички езици)
                        if (config.getAddonValue() != null) {
                            configDto.setAddonValue(modelMapper.map(config.getAddonValue(), WpAddonValueDto2.class));
                        }

                        return configDto;
                    }).toList());
        }

        return ResponseEntity.ok(dto);
    }

    private String translateInstructionContent(String from, String to) {

        return String.format("Преведи от %s на %s , Запази всички HTML тагове, емотикони и форматиране.", from, to);
    }

    @PostMapping("/translate/content")
    @Transactional
    public ResponseEntity<?> translateContent(@RequestBody ProductTranslateContentDTO request) {
        try {
            WpProductEntity product = wpProductRepository.getReferenceById(request.getProductId());

            List<LanguageEntity> languages = languageRepository.findAll();

            for (LanguageEntity language : languages) {
                if(Objects.equals(language.getId(), request.getItem().getLanguage().getId())) continue;

                LanguageEntity referenceById1 = languageRepository.getReferenceById(language.getId());

                WpProductTranslationEntity wpProductTranslationEntity = null;
                Optional<WpProductTranslationEntity> byProductAndLanguage = wpProductTranslationRepository.findByProductAndLanguage(product, referenceById1);
                if(byProductAndLanguage.isPresent()) {
                    wpProductTranslationEntity = byProductAndLanguage.get();
                }else {
                    wpProductTranslationEntity = new WpProductTranslationEntity();
                    wpProductTranslationEntity.setLanguage(language);
                    wpProductTranslationEntity.setProduct(product);
                }

                if (request.getType() == 1L) {
                    String translatedTitle = chatGptService.translateText(request.getItem().getName(), translateInstructionContent(request.getItem().getLanguage().getName(), language.getName()));
//                System.out.println(translatedTitle);
                    wpProductTranslationEntity.setName(translatedTitle);
                } else if (request.getType() == 2L) {
                    String translatedShortDescription = chatGptService.translateText(request.getItem().getShortDescription(), translateInstructionContent(request.getItem().getLanguage().getName(), language.getName()));
//                System.out.println(translatedShortDescription);
                    wpProductTranslationEntity.setShortDescription(translatedShortDescription);
                } else if (request.getType() == 3L) {
                    String translatedDescription = chatGptService.translateText(request.getItem().getDescription(), translateInstructionContent(request.getItem().getLanguage().getName(), language.getName()));
//                System.out.println(translatedDescription);
                    wpProductTranslationEntity.setDescription(translatedDescription);
                }
                wpProductTranslationRepository.save(wpProductTranslationEntity);
            }
            return ResponseEntity
                    .ok(true);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }

    }


    @PostMapping("/sync/{siteId}")
    public boolean syncWpProducts(@PathVariable Long siteId) {
        wpProductService.syncProductsToDB(siteId);
        return true;
    }

    @PostMapping("/sync/products/web/{siteId}")
    public boolean syncWpProductsToWeb(@PathVariable Long siteId) {
            wpProductService.syncProductsToSite(siteId);
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
