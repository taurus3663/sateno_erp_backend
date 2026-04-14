package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.service.ChatGptService;
import com.sateno_b.www.service.FileStorageService;
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
    private final FileStorageService fileStorageService;

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

    @Transactional
    public void deleteMultipleProducts(Long[] ids) {
        for (Long id : ids) {
            WpProductEntity product = wpProductRepository.findById(id).orElse(null);
            if (product == null) continue;

            // 1. Физическо изтриване на снимките от диска
            if (product.getImages() != null) {
                for (WpProductImageEntity img : product.getImages()) {
                    fileStorageService.deleteProductImage(img.getLocalSrc());
                }
            }

            // 2. Изтриване от базата
            // Благодарение на CascadeType.ALL в Entity-то, това ще изтрие автоматично:
            // - Translations
            // - AddonConfigs
            // - SiteConfigs
            // - ImageSiteMappings (ако са настроени правилно)
            wpProductRepository.delete(product);

            log.info("Продукт с ID {} и всички негови връзки бяха изтрити.", id);
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
// ПРЕМАХНИ @Transactional ТУК!
    public ResponseEntity<?> translateContent(@RequestBody ProductTranslateContentDTO request) {
        try {
            WpProductEntity product = wpProductRepository.findById(request.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            // 1. Записваме източника (използваме новия метод в сървиса)
            wpProductService.saveSingleTranslation(
                    request.getProductId(),
                    request.getItem().getLanguage().getId(),
                    request.getItem(),
                    request.getType()
            );

            List<LanguageEntity> languages = languageRepository.findAll();
            String sourceLangName = request.getItem().getLanguage().getName();
            String textToTranslate = "";

            // Определяме какво ще превеждаме веднъж
            if (request.getType() == 1L) textToTranslate = request.getItem().getName();
            else if (request.getType() == 2L) textToTranslate = request.getItem().getShortDescription();
            else if (request.getType() == 3L) textToTranslate = request.getItem().getDescription();

            for (LanguageEntity targetLanguage : languages) {
                if (Objects.equals(targetLanguage.getId(), request.getItem().getLanguage().getId())) continue;

                String instruction = translateInstructionContent(sourceLangName, targetLanguage.getName());

                // ChatGPT повикването е ИЗВЪН трансакция (това е добре)
                String translatedText = chatGptService.translateText(textToTranslate, instruction);

                // 2. Записваме превода в собствена малка трансакция
                wpProductService.saveTranslatedField(
                        request.getProductId(),
                        targetLanguage.getId(),
                        translatedText,
                        request.getType()
                );
            }

            return ResponseEntity.ok(true);
        } catch (Exception e) {
            log.error("Грешка при превод: ", e);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }


//    @PostMapping("/sync/{siteId}")
//    public boolean syncWpProducts(@PathVariable Long siteId) {
//        wpProductService.syncProductsToDB(siteId);
//        return true;
//    }

//    @PostMapping("/sync/to/{siteId}")
//    public boolean syncWpProductsToSite(@PathVariable Long siteId) {
//        wpProductService.syncProductsToSite(siteId);
//        return true;
//    }

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
