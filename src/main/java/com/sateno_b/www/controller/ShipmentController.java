package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.ShipmentCityDto;
import com.sateno_b.www.model.dto.ShipmentOfficeDto;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.interfaces.ShippingProvider;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.service.SpeedyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/shipment")
@RequiredArgsConstructor
public class ShipmentController {

    private final SpeedyService speedyService;
    private final CourierSettingsRepository courierSettingsRepository;



    @GetMapping("/cities/{courierId}")
    public List<ShipmentCityDto> getCities(
            @PathVariable Long courierId,
            @RequestParam(required = false, defaultValue = "") String query) {

        var courier = courierSettingsRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found"));

        List<ShipmentCityDto> rs = new ArrayList<>();
        if(courier.getCourierType() == CourierType.SPEEDY) {
              rs.addAll(speedyService.getCities(courier.getUsername(), courier.getPassword(), query));
        }
        return rs;
    }

    @GetMapping("/offices/{courierId}/{cityId}")
    public List<ShipmentOfficeDto> getOffices(
            @PathVariable Long courierId,
            @PathVariable Long cityId,
            @RequestParam(required = false, defaultValue = "") String query
    ) {
        var courier = courierSettingsRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found"));

        List<ShipmentOfficeDto> rs = new ArrayList<>();
        if(courier.getCourierType() == CourierType.SPEEDY) {
            rs.addAll(speedyService.getOffices(courier.getUsername(), courier.getPassword(), cityId, query));
        }

        return rs;
    }

//    @GetMapping("/city-by-id/{courierId}/{id}")
//public ShipmentCityDto getCityById(@PathVariable Long courierId, @PathVariable String id) {
//    Courier courier = courierRepository.findById(courierId).get();
//    ShippingProvider provider = providers.get(courier.getType().toLowerCase() + "Service");
//
//    // В SpeedyService трябва да направиш заявка към location/site/details
//    return provider.getCityById(id, courier.getUserName(), courier.getPassword());
//}
//
//@GetMapping("/office-by-id/{courierId}/{id}")
//public ShipmentOfficeDto getOfficeById(@PathVariable Long courierId, @PathVariable String id) {
//    Courier courier = courierRepository.findById(courierId).get();
//    ShippingProvider provider = providers.get(courier.getType().toLowerCase() + "Service");
//
//    return provider.getOfficeById(id, courier.getUserName(), courier.getPassword());
//}



}
