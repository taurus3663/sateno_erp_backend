package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.NekorektenResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Service
public class NekorektenService {

    private final RestClient restClient;
    private final String API_KEY = "53e54b69feea88d3ea7656928c5fa22861859307";
    private final String BASE_URL = "https://api.nekorekten.com/api/v1/reports";

    public NekorektenResponseDto checkPhone(String phone) {

        String cleanPhone = phone;
        if (cleanPhone.startsWith("0")) {
            cleanPhone = "359" + cleanPhone.substring(1);
        } else {
            cleanPhone = phone.replaceAll("\\D+", "");
        }

        try {
            return restClient.get()
                    .uri(BASE_URL + "?phone="+ cleanPhone)
                    .header("Api-Key", API_KEY)
                    .retrieve()
                    .body(NekorektenResponseDto.class);

        } catch (Exception e) {
            System.err.println("Грешка при проверка в nekorekten.com: " + e.getMessage());
            return null;
        }

    }
}
