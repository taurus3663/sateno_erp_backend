package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.ShipmentCityDto;
import com.sateno_b.www.model.dto.ShipmentOfficeDto;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.data.CourierContractDetails;
import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.interfaces.ShippingProvider;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
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
    private final CourierSettingsRepository courierSettingsRepository;


    public double calculatePrice(double weight, CourierShipmentType shipmentType, String username, String password, CourierSettingsEntity courierSettings) {
        try {
            Map<String, Object> body = createBaseBody(username, password);
            var contract = courierSettings.getCourierContractDetails();
            Map<String, Object> content = new HashMap<>();
            content.put("contents", "Текстилни изделия");
            content.put("parcelsCount", 1);   // <-- ТУК трябва да е
            content.put("totalWeight", 2.5);
            body.put("content", content);

            Map<String, Object> payment = new HashMap<>();

// кой плаща
            payment.put("courierServicePayer", "SENDER");

// начин на плащане
            payment.put("declaredValuePayer", "SENDER");

            body.put("payment", payment);

// Добави и тези, за да сме сигурни, че сесията е пълна
            body.put("parcelsCount", 1);

            // 1. ОСНОВНИ ПАРАМЕТРИ
            // Пробвай със serviceId 3 (Икономична), защото 502/505 понякога са забранени за нови акаунти
            long serviceId = (shipmentType == CourierShipmentType.ADDRESS) ? 2L : 3L;

            Map<String, Object> service = new HashMap<>();
            service.put("serviceIds", List.of(505L)); // ✅ ТУК Е КЛЮЧОВОТО
            service.put("autoAdjustPickupDate", true);

            body.put("service", service);


            body.put("shippingDate", java.time.LocalDate.now().toString());

            // 2. ПОДАТЕЛ (Използваме clientId)
            Map<String, Object> sender = new HashMap<>();
            sender.put("clientId", contract.getClientId());
            body.put("sender", sender);

            // 3. ПОЛУЧАТЕЛ
            Map<String, Object> recipient = new HashMap<>();
            recipient.put("privatePerson", true);
            recipient.put("siteId", 68134L); // София

            if (shipmentType == CourierShipmentType.ADDRESS) {
                recipient.put("addressLocation", Map.of("siteId", 68134L));
            } else {
                // Офис 10 е винаги валиден (Централна поща София)
                recipient.put("pickupOfficeId", 10L);
            }
            body.put("recipient", recipient);

            // 4. ПАКЕТИ (PARCELS) - Ключово за грешката "service.required"
            double calcWeight = (weight <= 0) ? 1.0 : weight;

            // Спиди изисква списък от обекти, всеки със 'size'
            List<Map<String, Object>> parcels = new ArrayList<>();
            Map<String, Object> parcel = new HashMap<>();
            Map<String, Object> size = new HashMap<>();
            size.put("weight", calcWeight);
            parcel.put("size", size);
            parcels.add(parcel);

            body.put("parcels", parcels);

            // 5. ДОПЪЛНИТЕЛНИ ПАРАМЕТРИ (Понякога критични)
            // Указваме изрично, че е 1 пакет
            body.put("totalWeight", calcWeight);

            // API повикване
            Map<String, Object> response = postToSpeedy("calculate", body);
            System.out.println("DEBUG SPEEDY PAYLOAD: " + body);
            System.out.println("DEBUG SPEEDY RESPONSE: " + response);

            if (response != null && response.containsKey("calculations")) {
                List<Map<String, Object>> calculations =
                        (List<Map<String, Object>>) response.get("calculations");

                if (!calculations.isEmpty()) {

                    Map<String, Object> calc = calculations.get(0);

                    if (calc.containsKey("error")) {
                        System.out.println("Speedy service error: " + calc.get("error"));
                        return 0.0;
                    }

                    Map<String, Object> price =
                            (Map<String, Object>) calc.get("price");

                    if (price != null && price.containsKey("total")) {
                        return Double.parseDouble(price.get("total").toString());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Speedy Calc Error: " + e.getMessage());
        }
        return 0.00;
    }
    /**
     * Резервна калкулация (Fallback), за да не е безплатна доставката при проблем с API
     */
//    private double calculateFallback(double weight, CourierShipmentType type) {
//        double base = (type == CourierShipmentType.ADDRESS) ? 7.50 : 5.50;
//        return base + (weight * 0.50);
//    }

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

    public boolean testLogin(String username, String password, Long courierId) {
        try {
            System.out.println("Тестване на връзка със Спиди за потребител: " + username);

            Map<String, Object> body = new HashMap<>();
            // ВАЖНО: Ключовете трябва да са точно userName и password
            body.put("userName", username);
            body.put("password", password);
            body.put("language", "BG");

            // За да тестваме дали са верни данните, просто искаме информация за държава България
            // Това е най-лекият метод за проверка на достъп.
//            var response = restClient.post()
////                    .uri("https://api.speedy.bg/v1/location/country") // ДОБАВИ ТОЗИ ПЪТ
//                    .uri("https://api.speedy.bg/v1/client/contract") // ДОБАВИ ТОЗИ ПЪТ
//                    .body(body)
//                    .retrieve()
//                    .toEntity(Object.class);
            var response =  postToSpeedy("client/contract", body);


            if (response != null && response.containsKey("clients")) {


                List<?> clientsList = (List<?>) response.get("clients");
                if (!clientsList.isEmpty()) {
                    // Взимаме първия клиент (обикновено е един за тези данни)
                    Map<String, Object> clientMap = (Map<String, Object>) clientsList.get(0);

                    // Мапваме към нашето DTO (можеш да ползваш Jackson ObjectMapper за по-лесно)
                    CourierContractDetails details = new CourierContractDetails();
                    details.setClientId(Long.parseLong(clientMap.get("clientId").toString()));
                    details.setClientName(clientMap.get("clientName").toString());
                    details.setObjectName(clientMap.get("objectName").toString());
                    details.setContactName(clientMap.get("contactName").toString());
                    details.setEmail(clientMap.get("email") != null ? clientMap.get("email").toString() : "");

                    // Мапване на адреса
                    if (clientMap.containsKey("address")) {
                        Map<String, Object> addrMap = (Map<String, Object>) clientMap.get("address");
                        CourierContractDetails.AddressDetails addr = new CourierContractDetails.AddressDetails();
                        addr.setSiteId(Long.parseLong(addrMap.get("siteId").toString()));
                        addr.setSiteName(addrMap.get("siteName").toString());
                        addr.setFullAddressString(addrMap.get("fullAddressString").toString());
                        addr.setPostCode(addrMap.get("postCode") != null ? addrMap.get("postCode").toString() : "");
                        details.setAddress(addr);
                    }

                    CourierSettingsEntity c = courierSettingsRepository.findById(courierId).orElse(null);
                    if (c != null) {
                        c.setCourierContractDetails(details);
                        courierSettingsRepository.save(c);
                    }
                }
                return true;
            } else return false;

//            System.out.println("Отговор от Спиди: " + response.getStatusCode());
//            System.out.println(response.getBody().toString());
            // Ако статусът е 2xx, значи userName и password са приети.
//            return response.getStatusCode().is2xxSuccessful();

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
