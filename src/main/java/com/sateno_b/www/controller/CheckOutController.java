package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CheckCourierRequest;
import com.sateno_b.www.model.dto.CheckOutCourierDto;
import com.sateno_b.www.model.dto.CheckOutCourierListDto;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.service.BoxNowService;
import com.sateno_b.www.service.EcontService;
import com.sateno_b.www.service.SpeedyService;
import com.sateno_b.www.service.WpOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    private final WpOrderService wpOrderService;

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
//             if(courier.getAutoShippingPrice() == true) {
//
////                System.out.println("works");
//                if(courier.getCourierType() == CourierType.SPEEDY && courier.isOffice() && courier.isOfficeAutoShippingPrice()) {
//                    double finalPrice = speedyService.calculatePriceDefault(request.getCart_weight(),  CourierShipmentType.OFFICE);
//                    courierDto.setFixedShippingPrice(finalPrice);
//                }
//                else if (courier.getCourierType() == CourierType.ECONT && courier.isAddress() && courier.isAddressAutoShippingPrice()) {
//                    System.out.println("111111");
//                    double finalPrice = econtService.calculatePriceDefault(request.getCart_weight(), CourierShipmentType.ADDRESS);
//                    System.out.println(finalPrice);
//                    courierDto.setFixedShippingPrice(finalPrice);
//
//                }
//                else if (courier.getCourierType() == CourierType.BOX_NOW && courier.isLocker() && courier.isLockerAutoShippingPrice()) {
//                    double finalPrice = boxNowService.calculatePriceDefault(request.getCart_weight(), CourierShipmentType.LOCKER);
//                    courierDto.setFixedShippingPrice(finalPrice);
//                }
//
//            }

             if(courier.isOfficeAutoShippingPrice()) {
                 if(courier.getCourierType() == CourierType.SPEEDY) {
                     double finalPrice = speedyService.calculatePriceDefault(request.getCart_weight(), CourierShipmentType.OFFICE);
                     courierDto.setOfficeFixedShippingPrice(finalPrice);
                 } else if(courier.getCourierType() == CourierType.ECONT) {
                     double finalPrice = econtService.calculatePriceDefault(request.getCart_weight(), CourierShipmentType.OFFICE);
                     courierDto.setOfficeFixedShippingPrice(finalPrice);
                 }
             }
             if(courier.isLockerAutoShippingPrice()) {
                 if(courier.getCourierType() == CourierType.SPEEDY) {
                     double finalPrice = speedyService.calculatePriceDefault(request.getCart_weight(),  CourierShipmentType.LOCKER);
                     courierDto.setLockerFixedShippingPrice(finalPrice);
                 }
             }
             if (courier.isAddressAutoShippingPrice()) {
                 if(courier.getCourierType() == CourierType.SPEEDY) {
                     double finalPrice = speedyService.calculatePriceDefault(request.getCart_weight(),  CourierShipmentType.ADDRESS);
                     courierDto.setAddressFixedShippingPrice(finalPrice);
                 } else if(courier.getCourierType() == CourierType.ECONT) {
                     double finalPrice = econtService.calculatePriceDefault(request.getCart_weight(), CourierShipmentType.ADDRESS);
                     courierDto.setAddressFixedShippingPrice(finalPrice);
                 }
             }



            dto.getCheckOutCourierList().add(courierDto);
        }

        List<CheckOutCourierDto> sortedList = dto.getCheckOutCourierList().stream()
                .sorted((c1, c2) -> {
                    Integer s1 = c1.getSortOrder() != null ? c1.getSortOrder() : Integer.MAX_VALUE;
                    Integer s2 = c2.getSortOrder() != null ? c2.getSortOrder() : Integer.MAX_VALUE;
                    return s1.compareTo(s2);
                })
                .toList();

        dto.setCheckOutCourierList(new ArrayList<>(sortedList));
//        System.out.println(dto.toString());
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

//    ERP SIDE
    @PostMapping("/recalculate-price")
    public double recalculate(@RequestBody CheckCourierRequest request) {
        double v = 0;
//        System.out.println(request.toString());
//        CheckCourierRequest(site=sateno.bg, cart_total=76, cart_weight=1.0, items_count=null,
//        items=[CheckOutCourierItemsDto(id=null, name=null, price=2.0, quantity=2, sku=a1890, weight=null)],
//        currency=EUR, targetId=null, cityName=София, postcode=1000, courierType=SPEEDY, courierShipmentType=null,
//        orderId=2093)


        if(request.getCourierType() == CourierType.SPEEDY) {
            v = speedyService.calculatePrice(request);
        } else if(request.getCourierType() == CourierType.ECONT) {
            v = econtService.calculatePrice(request);
        } else if(request.getCourierType() == CourierType.BOX_NOW) {
            v = 0.0;
        }

        return v;
    }

//    ERP/SITE SIDE
    @PostMapping("/recalculate-price-custom-field-shipping-price")
    public double recalculatePriceCustomFieldShippingPrice(@RequestBody CheckCourierRequest request) {
        double v = 0;
//        System.out.println(request.toString());
        //        CheckCourierRequest(site=sateno.bg, cart_total=76, cart_weight=1.0, items_count=null,
//        items=[CheckOutCourierItemsDto(id=null, name=null, price=2.0, quantity=2, sku=a1890, weight=null)],
//        currency=EUR, targetId=null, cityName=София, postcode=1000, courierType=SPEEDY, courierShipmentType=null,
//        orderId=2093)
       v = wpOrderService.checkCustomShippingField(request);


        return v;
    }

//    SITE SIDE
    @PostMapping("/recalculate-price2")
    public double recalculate2(@RequestBody CheckCourierRequest request) {
        double v = 0;
//        System.out.println(request.toString());
//        CheckCourierRequest(site=sateno.bg, cart_total=76, cart_weight=1.0, items_count=null,
//        items=[CheckOutCourierItemsDto(id=null, name=null, price=2.0, quantity=2, sku=a1890, weight=null)],
//        currency=EUR, targetId=null, cityName=София, postcode=1000, courierType=SPEEDY, courierShipmentType=null,
//        orderId=2093)


        if(request.getCourierType() == CourierType.SPEEDY) {
            v = speedyService.calculatePrice2(request);
        } else if(request.getCourierType() == CourierType.ECONT) {
            v = econtService.calculatePrice(request);
        } else if(request.getCourierType() == CourierType.BOX_NOW) {
            v = 0.0;
        }

        return v;
    }




}
