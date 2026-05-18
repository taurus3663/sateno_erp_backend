package com.sateno_b.www.shared;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageToWordPress {

    private final FileStorageService fileStorageService;
    private final RestClient restClient;

    public Long uploadImageToWordPress(SiteEntity site, String localPath) {
        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
        // Взимаме байтовете от локалния диск
        byte[] imageBytes = fileStorageService.getImageBytes(localPath);
        String fileName = localPath.substring(localPath.lastIndexOf("/") + 1);

        try {
            var response = restClient.post()
                    .uri(site.getUrlWithHttps() + "/wp-json/wp/v2/media")
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Disposition", "attachment; filename=" + fileName)
                    .header("Content-Type", "image/jpeg") // или динамично според разширението
                    .body(imageBytes)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            // Връща ID-то на новосъздадената медия в WordPress
            return Long.valueOf(response.get("id").toString());
        }
        catch (org.springframework.web.client.RestClientResponseException e) {
            // ТУК Е МАГИЯТА: Вземаме тялото на грешката (JSON-а от WordPress)
            String errorBody = e.getResponseBodyAsString();
            log.error("Грешка от WordPress API (Status: {}): {}", e.getStatusCode(), errorBody);

            // Можеш да логнеш и хедърите, ако се съмняваш в проксита или защитни стени
            log.debug("Headers: {}", e.getResponseHeaders());

            return null;
        }
        catch (Exception e) {
            log.error("Грешка при качване на снимка в WP: {}", e.getMessage());
            return null;
        }
    }

    public void deleteMediaOneByOne(SiteEntity site, Set<Long> mediaIds, String auth) {
        for (Long mediaId : mediaIds) {
            try {
                // force=true е задължително за медия, за да не отиде в Trash, а да се изтрие физически файлът
                restClient.delete()
                        .uri(site.getUrlWithHttps() + "/wp-json/wp/v2/media/" + mediaId + "?force=true")
                        .header("Authorization", "Basic " + auth)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                // Често една снимка е свързана с няколко продукта, затова ако вече е изтрита, просто игнорираме
                log.warn("Медия ID {} вече не съществува или не може да бъде изтрита.", mediaId);
            }
        }
        log.info("Изчистени {} медийни файла от WordPress.", mediaIds.size());
    }

    /**
     * НОВ МЕТОД: Физическо качване на видео в WordPress Media Library.
     * Връща Мап с 'id' и 'url' (source_url), които са ни нужни за WoodMart мета данните.
     */
    public Map<String, Object> uploadVideoToWordPress(SiteEntity site, String localPath) {
        String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());
        // Използваме същия метод за четене на байтове.
        // Ако видео файловете са много големи, се увери, че heap паметта на Java-та е достатъчна.
        byte[] videoBytes = fileStorageService.getImageBytes(localPath);
        String fileName = localPath.substring(localPath.lastIndexOf("/") + 1);

        // ДИНАМИЧЕН CONTENT-TYPE: Важно за сървъра на WordPress при обработка на MKV/MP4
        String contentType = "video/mp4";
        if (fileName.toLowerCase().endsWith(".mkv")) {
            contentType = "video/x-matroska";
        }

        try {
            log.info("🚀 [API] Стартира качване на видео: {} ({}) към {}", fileName, contentType, site.getUrl());

            var response = restClient.post()
                    .uri(site.getUrlWithHttps() + "/wp-json/wp/v2/media")
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Disposition", "attachment; filename=" + fileName)
                    .header("Content-Type", contentType)
                    .body(videoBytes)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("id")) {
                Map<String, Object> result = new HashMap<>();
                result.put("id", Long.valueOf(response.get("id").toString()));
                result.put("url", response.get("source_url").toString());

                log.info("✅ [API] Видеото е качено успешно в WP! ID: {}, URL: {}", result.get("id"), result.get("url"));
                return result;
            }
        }
        catch (org.springframework.web.client.RestClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("❌ Грешка от WordPress API при видео (Status: {}): {}", e.getStatusCode(), errorBody);
            // Ако лимитът на хостинга е малък, тук ще видиш грешка "413 Payload Too Large"
        }
        catch (Exception e) {
            log.error("❌ Критична грешка при качване на видео в WP: {}", e.getMessage());
        }
        return null;
    }

}
