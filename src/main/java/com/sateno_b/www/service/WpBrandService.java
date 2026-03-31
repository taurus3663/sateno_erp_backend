package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.WpBrandDto;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpBrandEntity;
import com.sateno_b.www.model.entity.WpBrandWpIdEntity;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpBrandRepository;
import com.sateno_b.www.model.repository.WpBrandWpIdRepository;
import com.sateno_b.www.shared.SlugTool;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WpBrandService {

    private final SiteRepository siteRepository;
    private final RestClient restClient;
    private final WpBrandRepository wpBrandRepository;
    private final WpBrandWpIdRepository wpBrandWpIdRepository;

    private static final String BRANDS_URL = "/wp-json/wc/v3/products/brands";

    @Transactional
    public void syncBrandsToDB(Long siteId) {

        SiteEntity site = siteRepository.findById(siteId).orElse(null);
        if(site == null) {return;}
        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

        List<WpBrandDto> allBrands = fetchAllBrands(site, auth);

        for (WpBrandDto brandDto : allBrands) {
            String decodedSlug = SlugTool.decodeSlug(brandDto.getSlug());

            // 1. Първо търсим дали изобщо имаме такава марка в глобалната таблица (по slug)
            WpBrandEntity brandEntity = wpBrandRepository.findBySlug(decodedSlug)
                    .orElseGet(() -> {
                        // Ако я няма, създаваме новата глобална марка
                        WpBrandEntity newBrand = new WpBrandEntity();
                        newBrand.setName(brandDto.getName());
                        newBrand.setSlug(decodedSlug);
                        newBrand.setDescription(brandDto.getDescription());
                        // WooCommerce често праща обекти за снимки, увери се че DTO-то ги мапва
                        newBrand.setImageUrl(brandDto.getImageUrl());
                        return wpBrandRepository.save(newBrand);
                    });

            // 2. СЕГА най-важното: Проверяваме дали имаме запис в свързващата таблица за ТОЗИ сайт и ТОВА WP_ID
            // Трябва ти метод в WpBrandWpIdRepository: findBySiteAndWpId(SiteEntity site, Long wpId)
            Optional<WpBrandWpIdEntity> existingMapping = wpBrandWpIdRepository.findBySiteAndWpId(site, brandDto.getId());

            if (existingMapping.isEmpty()) {
                // Ако няма такъв мапинг, създаваме го
                WpBrandWpIdEntity newMapping = new WpBrandWpIdEntity();
                newMapping.setSite(site);
                newMapping.setWpId(brandDto.getId()); // Тук записваме ID-то от WordPress (напр. 144)
                newMapping.setBrand(brandEntity);     // Сочи към глобалния бранд

                wpBrandWpIdRepository.save(newMapping);
            }
//            if(existingMapping.isPresent()) {
//                WpBrandWpIdEntity etity =  existingMapping.get();
//                etity.setBrand(brandEntity);
//                etity.setWpId(brandDto.getId());
//            }
        }

    }

    private List<WpBrandDto> fetchAllBrands(SiteEntity site, String auth) {
        List<WpBrandDto> allBrands = new ArrayList<>();
        int currentPage = 1;
        int totalPages = 1;

        do {

            var response = restClient.get()
                    .uri(site.getUrlWithHttps() + BRANDS_URL + "?per_page=100&page=" + currentPage + "&orderby=id&order=asc")
                    .header("Authorization", "Basic " + auth)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<WpBrandDto>>() {});

            if(response.getBody() != null) {
                allBrands.addAll(response.getBody());
            }

            String totalPageHeader = response.getHeaders().getFirst("X-WP-TotalPages");
            if(totalPageHeader != null) {
                totalPages = Integer.parseInt(totalPageHeader);
//                System.out.println("totalPAges: " + totalPages);
            }

            currentPage++;
        } while(currentPage <= totalPages);

        return allBrands;
    }
}
