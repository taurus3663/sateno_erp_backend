package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.CheckCourierRequest;
import com.sateno_b.www.model.dto.ShipmentCityDto;
import com.sateno_b.www.model.dto.ShipmentOfficeDto;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.interfaces.ShippingProvider;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@RequiredArgsConstructor
@Service
public class BoxNowService implements ShippingProvider {

    private final RestClient restClient;
    private final SiteRepository siteRepository;
    private final CourierSettingsRepository courierSettingsRepository;

    @Override
    public List<ShipmentCityDto> getCities(String username, String password, String nameFilter) {
        try {
            Map<String, Object> response = getToBoxNow("/api/v1/destinations", Map.of(), username, password);
            if (response == null || !response.containsKey("data")) return List.of();

            List<Map<String, Object>> destinations = (List<Map<String, Object>>) response.get("data");
            Map<String, ShipmentCityDto> cities = new HashMap<>();

            for (Map<String, Object> dest : destinations) {
                Object cityObj = dest.get("addressLine2");
                if (cityObj == null) continue; // игнорираме ако няма град
                String cityName = cityObj.toString().trim();
                if (cityName.isEmpty()) continue;

                if (nameFilter != null && !nameFilter.isBlank() &&
                        !cityName.toLowerCase().contains(nameFilter.toLowerCase())) {
                    continue;
                }

                if (!cities.containsKey(cityName)) {
                    ShipmentCityDto dto = new ShipmentCityDto();
                    dto.setId((long) (cityName.hashCode() & 0xfffffff)); // генерираме уникално id
                    dto.setName(cityName);
                    cities.put(cityName, dto);
                }

                if (cities.size() >= 10) break; // лимит 10
            }

            return List.copyOf(cities.values());

        } catch (Exception e) {
            System.err.println("BoxNow getCities error: " + e.getMessage());
            return List.of();
        }
    }


    @Override
    public List<ShipmentOfficeDto> getOffices(String username, String password, Long cityId, String nameFilter) {
        try {
            Map<String, Object> response = getToBoxNow("/api/v1/destinations", Map.of(), username, password);
            if (response == null || !response.containsKey("data")) return List.of();

            List<Map<String, Object>> destinations = (List<Map<String, Object>>) response.get("data");
            List<ShipmentOfficeDto> offices = new ArrayList<>();

            for (Map<String, Object> dest : destinations) {
                // Град
                Object cityObj = dest.get("addressLine2");
                if (cityObj == null) continue;
                String cityName = cityObj.toString().trim();
                if (cityName.isEmpty()) continue;

                // Сравняваме с cityId
                long destCityId = cityName.hashCode() & 0xfffffff;
                if (!destCityIdEquals(cityId, destCityId)) continue;

                // Филтър по име на офиса
                Object nameObj = dest.get("name");
                String officeName = (nameObj != null) ? nameObj.toString().trim() : "";
                if (nameFilter != null && !nameFilter.isBlank() &&
                        !officeName.toLowerCase().contains(nameFilter.toLowerCase())) {
                    continue;
                }

                ShipmentOfficeDto dto = new ShipmentOfficeDto();
                dto.setId(dest.get("id") != null ? Long.valueOf(dest.get("id").toString()) : null);
                dto.setName(officeName);

                // Адрес
                Object addr1 = dest.get("addressLine1");
                Object addr2 = dest.get("addressLine2");
                String address = ((addr1 != null ? addr1.toString() : "") +
                        (addr2 != null ? ", " + addr2.toString() : "")).trim();
                dto.setAddress(address);

                offices.add(dto);
            }

            return offices;

        } catch (Exception e) {
            System.err.println("BoxNow getOffices error: " + e.getMessage());
            return List.of();
        }
    }

    // В помощна функция сравняваме cityId
    private boolean destCityIdEquals(Long cityId, long destCityId) {
        if (cityId == null) return true; // ако няма филтър по град, връщаме всички
        return cityId == destCityId;
    }


    // Метод за сравнение, за да се избегнат проблеми с отрицателни хешове
    private boolean generatedCityIdEquals(Long requestedId, long generatedId) {
        return requestedId == generatedId;
    }





    private final String BASE_URL = "https://api-production.boxnow.bg";

    public String getAuthToken(String apiKey, String apiSecret) {
        try {
            String authUrl = BASE_URL + "/api/v1/auth-sessions";


            Map<String, String> body = new HashMap<>();
            body.put("grant_type", "client_credentials");
            body.put("client_id", apiKey);
            body.put("client_secret", apiSecret);

            var response = restClient.post()
                    .uri(authUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "application/json")
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String error = new String(res.getBody().readAllBytes());
                        System.err.println("BoxNow Production Error: " + error);
                    })
                    .body(Map.class);

            if (response != null && response.containsKey("access_token")) {
                return (String) response.get("access_token");
            }
        } catch (Exception e) {
            System.err.println("BoxNow Auth Error: " + e.getMessage());
        }
        return null;
    }

    public boolean testLogin(String apiKey, String apiSecret) {
        String token = getAuthToken(apiKey, apiSecret);
        return token != null && !token.isEmpty();
    }

    public double calculatePriceDefault(double weight, CourierShipmentType type) {
        double basePrice = 0;

        if (type == CourierShipmentType.OFFICE) {
            if (weight <= 3) basePrice = 4.05;
            else if (weight <= 5) basePrice = 4.88;
            else if (weight <= 10) basePrice = 6.44;
            else basePrice = 6.44 + (weight - 10) * 0.50;
        } else if (type == CourierShipmentType.ADDRESS) { // До адрес
            if (weight <= 3) basePrice = 5.95;
            else if (weight <= 5) basePrice = 7.20;
            else if (weight <= 10) basePrice = 10.50;
            else basePrice = 10.50 + (weight - 10) * 0.80;
        } else if ( type == CourierShipmentType.LOCKER ) {
            if (weight <= 3) basePrice = 3.20; // Примерна цена за малък пакет
            else if (weight <= 20) basePrice = 4.50;
            else basePrice = 6.00;
        }

        // Добавяме средна такса гориво (напр. 10%) и ДДС (20%)
        double fuelSurcharge = 1.10;
        return basePrice * fuelSurcharge * 1.20;
    }

    public double calculatePrice(CheckCourierRequest request) {
        SiteEntity site = siteRepository.findSiteEntityByUrl(request.getSite());
        Optional<CourierSettingsEntity> settingsOpt = courierSettingsRepository
                .findBySiteAndCourierTypeAndCourierShipmentTypeAndActiveTrue(site, request.getCourierType(), request.getCourierShipmentType());

        if (settingsOpt.isPresent()) {
            CourierSettingsEntity settings = settingsOpt.get();

            // 1. Проверка за безплатна доставка
            if (settings.getFreeShippingPriceMax() != null &&
                    settings.getFreeShippingPriceMax() < Double.parseDouble(request.getCart_total())) {
                return 0.0;
            }

            try {
                // 2. ОНЛАЙН ПРОВЕРКА КЪМ BOX NOW
                // Използваме /destinations с параметър requiredSize
                // Размери според тяхната документация: 1-Малък, 2-Среден, 3-Голям
                int size = (request.getCart_weight() <= 2) ? 1 : 2;

//                String url = "https://api-stage.boxnow.bg/api/v1/destinations?requiredSize=" + size;

                Map<String, Object> body =  new HashMap<>();
                // Правим GET заявка, за да видим дали BoxNow връща активни автомати
                Map<String, Object> response = postToBoxNow("/api/v1/destinations?requiredSize=" + size, body, settings.getApiKey(), settings.getApiSecret());

//                System.out.println(response);
                if (response != null && response.containsKey("data")) {
                    // Ако API-то върне данни, значи услугата е налична.
                    // BoxNow не връщат цена в този JSON, защото тя е фиксирана по договор.
                    // ТУК трябва да вземеш цената, която СЪЩЕСТВУВА в твоя договор с тях.
                    return (settings.getFixedShippingPrice() != null) ? settings.getFixedShippingPrice() : 3.50;
                }

            } catch (Exception e) {
                System.out.println("BoxNow Online Check Error: " + e.getMessage());
                return -1.0;
            }
        }
        return 0.0;
    }


    private Map<String, Object> postToBoxNow(String endpoint, Map<String, Object> body, String username, String password) {
        String totalUrl = BASE_URL+ endpoint;

        return restClient.post()
                .uri(totalUrl)
                .header("Authorization", "Bearer " + getAuthToken(username, password))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
    private Map<String, Object> getToBoxNow(String endpoint, Map<String, Object> body, String username, String password) {
        String totalUrl = BASE_URL+ endpoint;

        return restClient.get()
                .uri(totalUrl)
                .header("Authorization", "Bearer " + getAuthToken(username, password))
//                .contentType(MediaType.APPLICATION_JSON)
//                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

}
