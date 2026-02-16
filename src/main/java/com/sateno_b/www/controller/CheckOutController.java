package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CheckCourierRequest;
import com.sateno_b.www.model.dto.CheckOutCourierDto;
import com.sateno_b.www.model.dto.CheckOutCourierListDto;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.service.EcontService;
import com.sateno_b.www.service.SpeedyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/checkout")
public class CheckOutController {

    private final CourierSettingsRepository courierSettingsRepository;
    private final SiteRepository siteRepository;
    private final SpeedyService speedyService;
    private final EcontService econtService;

    @PostMapping("/check-couriers")
    public ResponseEntity<CheckOutCourierListDto> check(@RequestBody CheckCourierRequest request) {

//        System.out.println(request.toString());

        SiteEntity site = siteRepository.findSiteEntityByUrl(request.getSite());
        List<CourierSettingsEntity> couriers = courierSettingsRepository.findAllBySiteAndActive(site, true);

        CheckOutCourierListDto dto = new CheckOutCourierListDto();
        dto.setCurrencyName(site.getCurrency().getName());
        dto.setCurrencySymbol(site.getCurrency().getSymbol());

        for (CourierSettingsEntity courier : couriers) {
            CheckOutCourierDto courierDto = new CheckOutCourierDto();
            courierDto.setCourierType(courier.getCourierType());
            courierDto.setActive(courier.isActive());
            courierDto.setCourierShipmentType(courier.getCourierShipmentType());
            courierDto.setSortOrder(courier.getSortOrder());
            courierDto.setFixedShippingPrice(courier.getFixedShippingPrice());
            courierDto.setFreeShippingPriceMax(courier.getFreeShippingPriceMax());
            courierDto.setAutoShippingPrice(courier.getAutoShippingPrice());

            if(courier.getAutoShippingPrice() == true) {
                System.out.println("works");
                if(courier.getCourierType() == CourierType.SPEEDY) {


//                   double finalPrice = speedyService.calculatePrice(request.getCart_weight(), courier.getCourierShipmentType(), courier.getUsername(), courier.getPassword(), courier);
//                    System.out.printf("fPrice: %f\n", finalPrice);
                    double finalPrice = speedyService.calculatePriceDefault(request.getCart_weight(),  courier.getCourierShipmentType());
                    courierDto.setFixedShippingPrice(finalPrice);
                }

            }

            dto.getCheckOutCourierList().add(courierDto);
        }


        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @PostMapping("/recalculate-price")
    public double recalculate(@RequestBody CheckCourierRequest request) {
        double v = 0;
        if(request.getCourierType() == CourierType.SPEEDY) {
            v = speedyService.calculatePrice(request);
        } else if(request.getCourierType() == CourierType.ECONT) {
            v = econtService.calculatePrice(request);
        }

        return v;
    }

}
