package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CourierSettingsDto;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.service.BoxNowService;
import com.sateno_b.www.service.EcontService;
import com.sateno_b.www.service.SpeedyService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/courier")
public class CourierSettingsController {

    private final CourierSettingsRepository courierSettingsRepository;
    private final ModelMapper modelMapper;
    private final SpeedyService speedyService;
    private final EcontService econtService;
    private final BoxNowService boxNowService;
    private final SiteRepository siteRepository;

    @PostMapping("/save")
    public ResponseEntity<CourierSettingsDto> save(@RequestBody CourierSettingsDto courierSettingsDto) {

        // 1. Създаване на нов куриер
        if (courierSettingsDto.getId() == null || courierSettingsDto.getId() == 0) {
            CourierSettingsEntity entity = modelMapper.map(courierSettingsDto, CourierSettingsEntity.class);

            // Важно: ModelMapper автоматично ще мапне site.id, ако DTO-то го има.
            // Hibernate ще разпознае, че това е съществуващ запис само по ID.
            CourierSettingsEntity savedEntity = courierSettingsRepository.save(entity);
            return ResponseEntity.ok(modelMapper.map(savedEntity, CourierSettingsDto.class));
        }

        // 2. Редакция на съществуващ куриер
        return courierSettingsRepository.findById(courierSettingsDto.getId())
                .map(en -> {
                    // 1. Ръчно се справяме с референцията към обекта
                    if (courierSettingsDto.getSite() == null) {
                        en.setSite(null); // Казваме на JPA, че връзката е премахната
                    } else {
                        SiteEntity referenceById = siteRepository.getReferenceById(courierSettingsDto.getSite().getId());
                        en.setSite(referenceById);
                    }

                    // 2. Мапваме останалите полета
                    // Важно: Настрой ModelMapper да прескача 'site' полето при мапване,
                    // за да не презапише току-що зададения null с грешна стойност
                    modelMapper.map(courierSettingsDto, en);

                    CourierSettingsEntity u = courierSettingsRepository.save(en);
                    return ResponseEntity.ok(modelMapper.map(u, CourierSettingsDto.class));
                }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/list")
    public ResponseEntity<Page<CourierSettingsDto>> list(Pageable pageable) {

        Page<CourierSettingsEntity> d = courierSettingsRepository.findAll(pageable);
//        Page<CurrencyDto> dtoPage = list.map(entity -> modelMapper.map(entity, CurrencyDto.class));
        Page<CourierSettingsDto> dtos = d.map(mapper -> modelMapper.map(mapper, CourierSettingsDto.class));

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody CourierSettingsDto courierSettingsDto) {
        boolean isSucessful = false;
        String message;

//        System.out.println(courierSettingsDto.toString());
        try {
            if(courierSettingsDto.getCourierType() == CourierType.SPEEDY) {
                isSucessful = speedyService.testLogin(courierSettingsDto.getUsername(), courierSettingsDto.getPassword(), courierSettingsDto.getId());
            }
            else if(courierSettingsDto.getCourierType() == CourierType.ECONT) {
                isSucessful = econtService.testLogin(courierSettingsDto.getUsername(), courierSettingsDto.getPassword(), courierSettingsDto.getId());
            }
            else if(courierSettingsDto.getCourierType() == CourierType.BOX_NOW) {
                isSucessful = boxNowService.testLogin(courierSettingsDto.getApiKey(), courierSettingsDto.getApiSecret());
            }
            message = isSucessful ? "Връзката е успешна!" : "Неуспешен вход. Проверете данните.";
        } catch (Exception e) {
            System.out.println(e.getMessage());
            message = e.getMessage();
        }

        return ResponseEntity.ok(Map.of(
                "success", isSucessful,
                "message", message
        ));
    }


}
