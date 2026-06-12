package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.NekorektenResponseDto;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.repository.WpOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class NekorektenService {

    private final RestClient restClient;
    private final WpOrderRepository wpOrderRepository;
    private final String API_KEY = "53e54b69feea88d3ea7656928c5fa22861859307";
    private final String BASE_URL = "https://api.nekorekten.com/api/v1/reports";

    public NekorektenResponseDto checkPhone(String phone) {
        String cleanPhone = cleanPhoneNumber(phone);
        try {
            return restClient.get()
                    .uri(BASE_URL + "?phone=" + cleanPhone)
                    .header("Api-Key", API_KEY)
                    .retrieve()
                    .body(NekorektenResponseDto.class);
        } catch (Exception e) {
            System.err.println("Грешка при проверка в nekorekten.com: " + e.getMessage());
            return null;
        }
    }

    public boolean sendReport(Long orderId, String text) {
        WpOrderEntity order = wpOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Поръчката не е намерена"));

        if (order.getSignalText() != null) {
            throw new RuntimeException("Вече има изпратен сигнал за тази поръчка");
        }

        String phone = cleanPhoneNumber(order.getBilling().getPhone());

        try {
            Map<String, String> body = new HashMap<>();
            body.put("phone", phone);
            body.put("text", text);

            restClient.post()
                    .uri(BASE_URL)
                    .header("Api-Key", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            order.setSignalText(text);
            wpOrderRepository.save(order);
            return true;
        } catch (Exception e) {
            System.err.println("Грешка при изпращане на сигнал: " + e.getMessage());
            return false;
        }
    }

    private String cleanPhoneNumber(String phone) {
        if (phone == null) return "";
        String clean = phone.replaceAll("\\D+", "");
        if (clean.startsWith("0")) {
            return "359" + clean.substring(1);
        }
        return clean;
    }
}
