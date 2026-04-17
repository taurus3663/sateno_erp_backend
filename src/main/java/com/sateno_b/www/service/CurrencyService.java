package com.sateno_b.www.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class CurrencyService {

    private final RestClient restClient;
    private final String API_URL = "https://api.frankfurter.dev/v2/rate";

    private final Map<String, CachedRate> cache = new ConcurrentHashMap<>();
    private final long CACHE_DURATION_MINUTES = 10; // Колко време да пазим курса
    private record CachedRate(BigDecimal rate, LocalDateTime timestamp) {}

//    public BigDecimal convert(BigDecimal amount, String from, String to) {
//        if(amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
//        if(from.equalsIgnoreCase(to)) return amount;
//
//        try {
//            Map<String, Object> response = restClient.get()
//                    .uri(API_URL + "/{from}/{to}", from.toUpperCase(), to.toUpperCase())
//                    .retrieve()
//                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
//
//            System.out.println(response);
//
//            if (response != null && response.containsKey("rate")) {
//                // Вземаме числото 5.0923 от мапата
//                Object rateObj = response.get("rate");
//                BigDecimal rate = new BigDecimal(rateObj.toString());
//
//                // Изчисляваме: 50 (amount) * 5.0923 (rate) = 254.615
//                BigDecimal result = amount.multiply(rate);
//
//                // Закръгляме до 2 знака след запетаята (заради валутата) -> 254.62
//                return result.setScale(2, RoundingMode.HALF_UP);
//            }
//        } catch (Exception e) {
//            log.error("Грешка: {}", e.getMessage());
//        }
//        return amount;
//    }

    public Double getRate(String from, String to) {
        if (from.equalsIgnoreCase(to)) return 1.0;

        try {
            // Използваме същия v2 ендпоинт
            Map<String, Object> response = restClient.get()
                    .uri(API_URL + "/{from}/{to}", from.toUpperCase(), to.toUpperCase())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("rate")) {
                // Връщаме чистия Double
                return Double.valueOf(response.get("rate").toString());
            }
        } catch (Exception e) {
            log.error("Грешка при вземане на курс: {}", e.getMessage());
        }
        return 1.0;
    }

    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        if (from.equalsIgnoreCase(to)) return amount;

        String cacheKey = from.toUpperCase() + "_" + to.toUpperCase();
        BigDecimal rate = getRateFromCacheOrApi(cacheKey, from, to);

        if (rate != null) {
            return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }

        return amount;
    }

    private BigDecimal getRateFromCacheOrApi(String cacheKey, String from, String to) {
        CachedRate cached = cache.get(cacheKey);

        // Проверяваме дали имаме запис и дали не е изтекъл (преди повече от 10 мин)
        if (cached != null && cached.timestamp.isAfter(LocalDateTime.now().minusMinutes(CACHE_DURATION_MINUTES))) {
            return cached.rate;
        }

        // Ако нямаме или е стар -> викаме API-то
        try {
            Map<String, Object> response = restClient.get()
                    .uri(API_URL + "/{from}/{to}", from.toUpperCase(), to.toUpperCase())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("rate")) {
                BigDecimal freshRate = new BigDecimal(response.get("rate").toString());
                // Записваме в кеша с текущото време
                cache.put(cacheKey, new CachedRate(freshRate, LocalDateTime.now()));
                return freshRate;
            }
        } catch (Exception e) {
            log.error("Грешка при API: {}", e.getMessage());
            // Ако API-то се счупи, но имаме СТАР курс в кеша, по-добре ползвай него вместо нищо
            if (cached != null) return cached.rate;
        }

        return null;
    }
}
