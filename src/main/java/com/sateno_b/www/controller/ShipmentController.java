package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.ShipmentCityDto;
import com.sateno_b.www.model.interfaces.ShippingProvider;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.service.SpeedyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/shipment")
@RequiredArgsConstructor
public class ShipmentController {

    private final SpeedyService speedyService;
    private final Map<String, ShippingProvider> providers;
    private final CourierSettingsRepository courierSettingsRepository;



    @GetMapping("/cities/{courierId}")
    public List<ShipmentCityDto> getCities(
            @PathVariable Long courierId,
            @RequestParam(required = false, defaultValue = "") String query) {

        // 1. Намираме настройките на куриера (потребител/парола) от БД
        var courier = courierSettingsRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found"));

        // 2. Вземаме правилния сървиз (напр. "speedyService")
        // Ако в БД типът е "SPEEDY", търсим "speedyService" в мапа
        ShippingProvider provider = providers.get(courier.getCourierType().name().toLowerCase() + "Service");

        // 3. Викаме сървиза с данните от БД
        return provider.getCities(query, courier.getUserName(), courier.getPassword());
    }



}
