package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CourierSettingsDto;
import com.sateno_b.www.model.dto.SiteDto;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.EmailEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/site")
@RequiredArgsConstructor
public class SiteController {

    private final SiteRepository siteRepository;
    private final ModelMapper modelMapper;
    private final CurrencyRepository currencyRepository;
    private final LanguageRepository languageRepository;
    private final CourierSettingsRepository courierSettingsRepository;
    private final EmailRepository emailRepository;

    @GetMapping("/list")
    public ResponseEntity<Page<SiteDto>> getAllSites(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<SiteDto> list = siteRepository.findAll(pageable)
                .map(s -> {
                    List<CourierSettingsDto> allBySite = courierSettingsRepository.findAllBySiteOrderBySortOrderAsc(s)
                            .stream().map(d -> modelMapper.map(d, CourierSettingsDto.class)).toList();

                    SiteDto siteDto = modelMapper.map(s, SiteDto.class);
                    siteDto.setCouriers(allBySite);
                    return siteDto;
                });

        return ResponseEntity.ok(list);
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<SiteDto> getSiteById(@PathVariable Long id) {
        Optional<SiteEntity> siteEntity = siteRepository.findById(id);
        SiteDto siteDto = modelMapper.map(siteEntity, SiteDto.class);
        return ResponseEntity.ok(siteDto);
    }

    @Transactional
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
            siteEntity.setOrderCreateApiKey(siteDto.getOrderCreateApiKey());

            siteEntity.setChangeStatusTimer(siteDto.getChangeStatusTimer());
            siteEntity.setSecondOrderMessage(siteDto.getSecondOrderMessage());
            siteEntity.setSecondOrderMessageTimer(siteDto.getSecondOrderMessageTimer());
            siteEntity.setThirdOrderMessage(siteDto.getThirdOrderMessage());
            siteEntity.setThirdOrderMessageTimer(siteDto.getThirdOrderMessageTimer());

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
        
        
        if(!siteDto.getCouriers().isEmpty()) {
            for (CourierSettingsDto courier : siteDto.getCouriers()) {
                Optional<CourierSettingsEntity> byId = courierSettingsRepository.findById(courier.getId());
                if(byId.isPresent()) {
                    byId.get()
                            .setActive(courier.isActive());
                    byId.get().setAutoShippingPrice(courier.getAutoShippingPrice());
                    byId.get().setFixedShippingPrice(courier.getFixedShippingPrice());
                    byId.get().setFreeShippingPriceMax(courier.getFreeShippingPriceMax());
                    byId.get().setSortOrder(courier.getSortOrder());
                    byId.get().setFreeShippingPriceMaxBol(courier.getFreeShippingPriceMaxBol());
                    courierSettingsRepository.save(byId.get());
                }
            }
        }

        Optional<EmailEntity> email = emailRepository.findById(siteDto.getEmail().getId());
        email.ifPresent(siteEntity::setEmail);

        siteEntity.setNewOrderMessage(siteDto.getNewOrderMessage());

        SiteEntity saved = siteRepository.save(siteEntity);
        return ResponseEntity.ok(modelMapper.map(saved, SiteDto.class));
    }
}
