package com.sateno_b.www.model.interfaces;

import com.sateno_b.www.model.dto.ShipmentCityDto;
import com.sateno_b.www.model.dto.ShipmentOfficeDto;

import java.util.List;

public interface ShippingProvider {

    void generateWayBill(Long orderId, Long siteId);
    String getStatus(String wayBillNumber);
    List<ShipmentCityDto> getCities(String nameFilter, String username, String password);
    List<ShipmentOfficeDto> getOffices(String nameFilter, String username, String password);

}
