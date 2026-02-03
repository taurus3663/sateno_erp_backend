package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class WebHookService {

    private final SiteRepository siteRepository;

    public Long validateAndGetSiteId(String payload, String signature) {
        List<SiteEntity> activeSites = siteRepository.findByActiveTrue();
        return getSiteAuthId(payload, signature, activeSites);
    }

    // Вече не е static, за да е консистентно със Service слоя
    private Long getSiteAuthId(String payload, String signature, List<SiteEntity> allActiveSites) {
        if (signature == null || payload == null) return null;

        for (SiteEntity site : allActiveSites) {
            String secret = site.getOrderCreateApiKey();
            if (secret == null || secret.isEmpty()) continue;

            String computedSignature = calculateHmacSha256(payload, secret);

            if (computedSignature.equals(signature)) {
                log.info("Успешна верификация за сайт: {}", site.getUrl());
                return site.getId();
            }
        }
        log.warn("Неуспешна верификация на уеб-кука. Подписът не съвпада с нито един активен сайт.");
        return null;
    }

    private String calculateHmacSha256(String payload, String secret) {
        try {
            final String ALGORITHM = "HmacSHA256";
            SecretKeySpec signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);

            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(signingKey);

            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            log.error("Грешка при изчисляване на HMAC-SHA256: {}", e.getMessage());
            return "";
        }
    }
}
