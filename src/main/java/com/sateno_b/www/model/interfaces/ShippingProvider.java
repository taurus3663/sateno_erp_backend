package com.sateno_b.www.model.interfaces;

import com.sateno_b.www.model.dto.ShipmentCityDto;
import com.sateno_b.www.model.dto.ShipmentOfficeDto;

import java.util.List;

public interface ShippingProvider {

    List<ShipmentCityDto> getCities(String username, String password, String nameFilter);
    List<ShipmentOfficeDto> getOffices(String username, String password, Long cityId, String nameFilter);

}
