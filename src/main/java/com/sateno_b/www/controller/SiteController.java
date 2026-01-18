package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.SiteDto;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.repository.CurrencyRepository;
import com.sateno_b.www.model.repository.LanguageRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/site")
@RequiredArgsConstructor
public class SiteController {

    private final SiteRepository siteRepository;
    private final ModelMapper modelMapper;
    private final CurrencyRepository currencyRepository;
    private final LanguageRepository languageRepository;

    @GetMapping("/list")
    public ResponseEntity<Page<SiteDto>> getAllSites(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SiteEntity> list = siteRepository.findAll(pageable);
        Page<SiteDto> dtoPage = list.map(entity -> modelMapper.map(entity, SiteDto.class));
        return ResponseEntity.ok(dtoPage);
    }

    @PostMapping("/save")
    public ResponseEntity<SiteDto> saveSite(@RequestBody SiteDto siteDto) {
        SiteEntity siteEntity;

        if (siteDto.getId() == null || siteDto.getId() == 0) {
            // При нов запис ModelMapper е ОК
            siteEntity = modelMapper.map(siteDto, SiteEntity.class);
        } else {
            // ПРИ РЕДАКЦИЯ: Намираме оригиналния обект
            siteEntity = siteRepository.findById(siteDto.getId())
                    .orElseThrow(() -> new RuntimeException("Сайтът не е намерен"));

            // Ръчно прехвърляме простите полета от DTO към Entity
            siteEntity.setName(siteDto.getName());
            siteEntity.setUrl(siteDto.getUrl());
            siteEntity.setConsumerKey(siteDto.getConsumerKey());
            siteEntity.setConsumerSecret(siteDto.getConsumerSecret());
            siteEntity.setActive(siteDto.isActive());

            // ВАЖНО: Сетваме валутата само ако е изпратена
            if (siteDto.getCurrency() != null && siteDto.getCurrency().getId() != null) {
                currencyRepository.findById(siteDto.getCurrency().getId())
                        .ifPresent(siteEntity::setCurrency);
            }
            if(siteDto.getLanguage() != null && siteDto.getLanguage().getId() != null) {
                languageRepository.findById(siteDto.getLanguage().getId())
                        .ifPresent(siteEntity::setLanguage);
            }
        }

        // При нов запис също е добре да подсигурим релацията,
        // за да не се опитва Hibernate да прави Cascade Persist
        if (siteEntity.getCurrency() != null && siteEntity.getCurrency().getId() != null) {
            currencyRepository.findById(siteEntity.getCurrency().getId())
                    .ifPresent(siteEntity::setCurrency);
        }
        if(siteEntity.getLanguage() != null && siteEntity.getLanguage().getId() != null) {
            languageRepository.findById(siteEntity.getLanguage().getId())
                    .ifPresent(siteEntity::setLanguage);
        }

        SiteEntity saved = siteRepository.save(siteEntity);
        return ResponseEntity.ok(modelMapper.map(saved, SiteDto.class));
    }
}
