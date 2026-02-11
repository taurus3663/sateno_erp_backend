package com.sateno_b.www.service;

import com.sateno_b.www.model.interfaces.ShippingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
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
}
