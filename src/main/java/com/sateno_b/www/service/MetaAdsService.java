package com.sateno_b.www.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetaAdsService {

    private final RestClient restClient;
    private static final String API_VERSION = "v25.0";
    private static final String BASE_URL = "https://graph.facebook.com/" + API_VERSION + "/";

    // А) Списък с всички рекламни акаунти, до които имаш достъп
    public Map<String, Object> getMyAdAccounts(String accessToken) {
        return fetchFromMeta("me/adaccounts", accessToken);
    }

    // Б) Статистика за конкретен рекламен акаунт (Insights)
    public Map<String, Object> getCampaignInsights(String adAccountId, String accessToken) {
        String accountId = adAccountId.startsWith("act_") ? adAccountId : "act_" + adAccountId;
        String dateRangeJson = "{\"since\":\"2026-05-01\",\"until\":\"2026-05-25\"}";
        String encodedDateRange = UriUtils.encode(dateRangeJson, StandardCharsets.UTF_8);
        String endpoint = accountId + "/insights?level=campaign&fields=campaign_name,spend,clicks,impressions,cpc,cpm,ctr";
//                "&time_range=" + encodedDateRange;
        return fetchFromMeta(endpoint, accessToken);
    }

    // В) Универсален метод за заявки (избягваш повторението на хедърите)
    private Map<String, Object> fetchFromMeta(String endpoint, String accessToken) {
        return restClient.get()
                .uri(BASE_URL + endpoint)
                .headers(h -> {
                    h.setBearerAuth(accessToken);
                    h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("text/javascript")));
                })
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}