package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CourierSettingsDto;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.service.BoxNowService;
import com.sateno_b.www.service.EcontService;
import com.sateno_b.www.service.SpeedyService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/save")
    public ResponseEntity<CourierSettingsDto> save(@RequestBody CourierSettingsDto courierSettingsDto) {

        if(courierSettingsDto.getId() == null ||  courierSettingsDto.getId() == 0){

            CourierSettingsEntity en =  modelMapper.map(courierSettingsDto, CourierSettingsEntity.class);
            CourierSettingsDto dto = modelMapper.map(courierSettingsRepository.save(en), CourierSettingsDto.class);
            return ResponseEntity.ok(dto);
        }

        return courierSettingsRepository.findById(courierSettingsDto.getId())
                .map(en -> {
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

        try {
            if(courierSettingsDto.getCourierType() == CourierType.SPEEDY) {
                isSucessful = speedyService.testLogin(courierSettingsDto.getUsername(), courierSettingsDto.getPassword());
            }
            else if(courierSettingsDto.getCourierType() == CourierType.ECONT) {
                isSucessful = econtService.testLogin(courierSettingsDto.getUsername(), courierSettingsDto.getPassword());
            }
            else if(courierSettingsDto.getCourierType() == CourierType.BOX_NOW) {
                isSucessful = boxNowService.testLogin(courierSettingsDto.getApiKey(), courierSettingsDto.getApiSecret());
            }
            message = isSucessful ? "Връзката е успешна!" : "Неуспешен вход. Проверете данните.";
        } catch (Exception e) {
            message = e.getMessage();
        }

        return ResponseEntity.ok(Map.of(
                "success", isSucessful,
                "message", message
        ));
    }
}
