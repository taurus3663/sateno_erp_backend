package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CheckCourierRequest;
import com.sateno_b.www.model.dto.CheckOutCourierDto;
import com.sateno_b.www.model.dto.CheckOutCourierListDto;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.service.BoxNowService;
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
    private final BoxNowService boxNowService;

    @PostMapping("/check-couriers")
    public ResponseEntity<CheckOutCourierListDto> check(@RequestBody CheckCourierRequest request) {

//        System.out.println(request.toString());
        SiteEntity site = siteRepository.findSiteEntityByUrl(request.getSite());
        List<CourierSettingsEntity> couriers = courierSettingsRepository.findAllBySiteAndActiveAndDefaultCourierTrue(site, true);
        CheckOutCourierListDto dto = new CheckOutCourierListDto();
        dto.setCurrencyName(site.getCurrency().getName());
        dto.setCurrencySymbol(site.getCurrency().getSymbol());

        for (CourierSettingsEntity courier : couriers) {
//            System.out.println("courier: " + courier.toString());
            CheckOutCourierDto courierDto = new CheckOutCourierDto();
            courierDto.setCourierType(courier.getCourierType());
            courierDto.setActive(courier.isActive());
//            courierDto.setCourierShipmentType(courier.getCourierShipmentType());
            courierDto.setOffice(courier.isOffice());
            courierDto.setOfficeFreeShippingPriceMax(courier.getOfficeFreeShippingPriceMax());
            courierDto.setOfficeFreeShippingPriceMaxBol(courier.isOfficeFreeShippingPriceMaxBol());
            courierDto.setOfficeAutoShippingPrice(courier.isOfficeAutoShippingPrice());
            courierDto.setOfficeFixedShippingPrice(courier.getOfficeFixedShippingPrice());

            courierDto.setAddress(courier.isAddress());
            courierDto.setAddressFreeShippingPriceMax(courier.getAddressFreeShippingPriceMax());
            courierDto.setAddressFreeShippingPriceMaxBol(courier.isAddressFreeShippingPriceMaxBol());
            courierDto.setAddressAutoShippingPrice(courier.isAddressAutoShippingPrice());
            courierDto.setAddressFixedShippingPrice(courier.getAddressFixedShippingPrice());

            courierDto.setLocker(courier.isLocker());
            courierDto.setLockerFreeShippingPriceMax(courier.getLockerFreeShippingPriceMax());
            courierDto.setLockerFreeShippingPriceMaxBol(courier.isLockerFreeShippingPriceMaxBol());
            courierDto.setLockerAutoShippingPrice(courier.isLockerAutoShippingPrice());
            courierDto.setLockerFixedShippingPrice(courier.getLockerFixedShippingPrice());

            courierDto.setSortOrder(courier.getSortOrder());
//            courierDto.setFixedShippingPrice(courier.getFixedShippingPrice());
//            courierDto.setFreeShippingPriceMax(courier.getFreeShippingPriceMax());
//            courierDto.setAutoShippingPrice(courier.getAutoShippingPrice());

            if(courier.isAddressFreeShippingPriceMaxBol() && courier.getAddressFreeShippingPriceMax() < Double.parseDouble(request.getCart_total())){
                courierDto.setAddressFixedShippingPrice(0D);
            }
            if(courier.isOfficeFreeShippingPriceMaxBol() && courier.getOfficeFreeShippingPriceMax() < Double.parseDouble(request.getCart_total())){
                courierDto.setOfficeFixedShippingPrice(0D);
            }
            if(courier.isLockerFreeShippingPriceMaxBol() && courier.getLockerFreeShippingPriceMax() < Double.parseDouble(request.getCart_total())){
                courierDto.setLockerFixedShippingPrice(0D);
            }


//            if(courier.getFreeShippingPriceMaxBol() == true && courier.getFreeShippingPriceMax() < Double.parseDouble(request.getCart_total())) {
//                courierDto.setFixedShippingPrice(0.0);
//            }
             if(courier.getAutoShippingPrice() == true) {

//                System.out.println("works");
//                if(courier.getCourierType() == CourierType.SPEEDY) {
//                    double finalPrice = speedyService.calculatePriceDefault(request.getCart_weight(),  courier.getCourierShipmentType());
//                    courierDto.setFixedShippingPrice(finalPrice);
//                } else if (courier.getCourierType() == CourierType.ECONT) {
//                    double finalPrice = econtService.calculatePriceDefault(request.getCart_weight(), courier.getCourierShipmentType());
//                    courierDto.setFixedShippingPrice(finalPrice);
//
//                }
//                else if (courier.getCourierType() == CourierType.BOX_NOW) {
//                    double finalPrice = boxNowService.calculatePriceDefault(request.getCart_weight(), courier.getCourierShipmentType());
//                    courierDto.setFixedShippingPrice(finalPrice);
//                }

            }

            dto.getCheckOutCourierList().add(courierDto);
        }
//        System.out.println(dto.toString());
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @PostMapping("/recalculate-price")
    public double recalculate(@RequestBody CheckCourierRequest request) {
        System.out.println(request.toString());
        double v = 0;
        if(request.getCourierType() == CourierType.SPEEDY) {
            v = speedyService.calculatePrice(request);
        } else if(request.getCourierType() == CourierType.ECONT) {
            v = econtService.calculatePrice(request);
        }

        return v;
    }

}
