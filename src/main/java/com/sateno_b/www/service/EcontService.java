package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.ShipmentCityDto;
import com.sateno_b.www.model.dto.ShipmentOfficeDto;
import com.sateno_b.www.model.interfaces.ShippingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Service
public class EcontService implements ShippingProvider {

    private final RestClient restClient;

    @Override
    public void generateWayBill(Long orderId, Long siteId) {

    }

    @Override
    public String getStatus(String wayBillNumber) {
        return "";
    }

    @Override
    public List<ShipmentCityDto> getCities(String nameFilter, String username, String password) {
        return List.of();
    }

    @Override
    public List<ShipmentOfficeDto> getOffices(String username, String password, Long cityId, String nameFilter) {
        return List.of();
    }


    public boolean testLogin(String username, String password) {
        try {
            // За демо винаги ползвай този URL
//            String demoUrl = "https://demo.econt.com/ee/services/Profile/ProfileService.getClientProfiles.json";
            String demoUrl = "https://ee.econt.com/services/Profile/ProfileService.getClientProfiles.json";

            var response = restClient.post()
                    .uri(demoUrl)
                    .headers(headers -> {
                        headers.setBasicAuth(username, password);
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body("{}") // Еконт изисква поне празен обект
                    .retrieve()
                    .toEntity(String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("EcontService testLogin error {}", e.getMessage());
            return false;
        }
    }

}
