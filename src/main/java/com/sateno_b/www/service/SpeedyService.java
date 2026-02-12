package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.ShipmentCityDto;
import com.sateno_b.www.model.dto.ShipmentOfficeDto;
import com.sateno_b.www.model.interfaces.ShippingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class SpeedyService implements ShippingProvider {

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
        var body = createBaseBody(username, password);
        body.put("countryId", 100);
        body.put("name", nameFilter);

        Map<String, Object> response = postToSpeedy("location/site", body);
        List<Map<String, Object>> sites = (List<Map<String, Object>>) response.get("sites");

        return sites.stream()
                .map(s -> {
                   ShipmentCityDto shipmentCity = new ShipmentCityDto();
                    shipmentCity.setId(Long.parseLong(s.get("id").toString()));
                    shipmentCity.setName(s.get("name").toString());
                    shipmentCity.setPostCode(s.get("postCode").toString());
                    return shipmentCity;
                })
                .toList();
    }

    @Override
        public List<ShipmentOfficeDto> getOffices(String cityId, String username, String password) {
            var body = createBaseBody(username, password);
            body.put("siteId", cityId); // Филтрираме офисите по избрания град

            Map<String, Object> response = postToSpeedy("location/office", body);

            List<Map<String, Object>> offices = (List<Map<String, Object>>) response.get("offices");

            return offices.stream()
                    .map(o -> {
                        Map<String, Object> address = (Map<String, Object>) o.get("address");
                       ShipmentOfficeDto shipOffice = new ShipmentOfficeDto();
                        shipOffice.setId(Long.parseLong(o.get("id").toString()));
                        shipOffice.setName(o.get("name").toString());
                        shipOffice.setAddress(address.get("fullAddressString").toString());
                        return  shipOffice;
                    })
                    .toList();
        }

    public boolean testLogin(String username, String password) {
        try {
            System.out.println("Тестване на връзка със Спиди за потребител: " + username);

            Map<String, Object> body = new HashMap<>();
            // ВАЖНО: Ключовете трябва да са точно userName и password
            body.put("userName", username);
            body.put("password", password);
            body.put("language", "BG");

            // За да тестваме дали са верни данните, просто искаме информация за държава България
            // Това е най-лекият метод за проверка на достъп.
            var response = restClient.post()
                    .uri("https://api.speedy.bg/v1/location/country") // ДОБАВИ ТОЗИ ПЪТ
                    .body(body)
                    .retrieve()
                    .toEntity(Object.class);

            System.out.println("Отговор от Спиди: " + response.getStatusCode());

            // Ако статусът е 2xx, значи userName и password са приети.
            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            System.err.println("Грешка при теста към Спиди: " + e.getMessage());
            return false;
        }
    }


    private Map<String, Object> createBaseBody(String user, String pass) {
        Map<String, Object> body = new HashMap<>();
        body.put("userName", user);
        body.put("password", pass);
        body.put("language", "BG");
        return body;
    }

    private Map<String, Object> postToSpeedy(String endpoint, Map<String, Object> body) {
        return restClient.post()
                .uri("https://api.speedy.bg/v1/" + endpoint)
                .body(body)
                .retrieve()
                .body(Map.class);
    }
}
