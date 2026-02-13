package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.ShipmentCityDto;
import com.sateno_b.www.model.dto.ShipmentOfficeDto;
import com.sateno_b.www.model.interfaces.ShippingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
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
    public List<ShipmentCityDto> getCities(String username, String password, String nameFilter) {
        var body = createBaseBody(username, password);
        body.put("countryId", 100);
        body.put("name", nameFilter);

        Map<String, Object> response = postToSpeedy("location/site", body);
        if (response == null || !response.containsKey("sites")) {
            return List.of();
        }
        List<?> sitesList = (List<?>) response.get("sites");
        return sitesList.stream()
                .map(item -> (Map<String, Object>) item)
                .map(s -> {
                    ShipmentCityDto dto = new ShipmentCityDto();
                    dto.setId(Long.parseLong(s.get("id").toString()));
                    dto.setName(s.get("name").toString());
                    dto.setPostCode(s.get("postCode") != null ? s.get("postCode").toString() : "");
                    return dto;
                })
                .toList();
    }

    @Override
        public List<ShipmentOfficeDto> getOffices( String username, String password, Long cityId, String nameFilter) {
            var body = createBaseBody(username, password);
            body.put("siteId", cityId); // Филтрираме офисите по избрания град
            body.put("name", nameFilter);

        Map<String, Object> response = postToSpeedy("location/office", body);
        if (response == null || !response.containsKey("offices")) {
            return List.of();
        }
        List<?> sitesList = (List<?>) response.get("offices");
        return sitesList.stream()
                .map(item -> (Map<String, Object>) item)
                .map(s -> {
                    ShipmentOfficeDto dto = new ShipmentOfficeDto();
                    dto.setId(Long.parseLong(s.get("id").toString()));
                    dto.setName(s.get("name").toString());
//                    dto.setPostCode(s.get("postCode") != null ? s.get("postCode").toString() : "");
                    return dto;
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
            System.out.println(response.getBody().toString());
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
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON) // Задължително за Спиди
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
