package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CheckCourierRequest;
import com.sateno_b.www.model.dto.CheckOutCourierDto;
import com.sateno_b.www.model.dto.CheckOutCourierListDto;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.model.repository.SiteRepository;
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

    @PostMapping("/check-couriers")
    public ResponseEntity<CheckOutCourierListDto> check(@RequestBody CheckCourierRequest request) {

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
            dto.getCheckOutCourierList().add(courierDto);
        }


        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

}
