package com.sateno_b.www.service;

import com.sateno_b.www.model.interfaces.ShippingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class BoxNowService implements ShippingProvider {

    private final RestClient restClient;


    @Override
    public void generateWayBill(Long orderId, Long siteId) {

    }

    @Override
    public String getStatus(String wayBillNumber) {
        return "";
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
}
