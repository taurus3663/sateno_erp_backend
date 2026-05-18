package com.sateno_b.www.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
@RequiredArgsConstructor
@Log4j2
public class WhatsAppService {

    @Value("${whatsapp.api.url}")
    private String apiUrl; // https://msg.mobile-gw.com:9000

    @Value("${whatsapp.system.id}")
    private String systemId;

    @Value("${whatsapp.password}")
    private String password;

    @Value("${whatsapp.sender.id}")
    private String senderId;

    private final RestClient restClient;

    public String sendWhatsApp(String toPhone, String message) {
        // Нормализиране → международен формат
        String cleanPhone = toPhone.replaceAll("[^0-9]", "");
        if (cleanPhone.startsWith("0")) {
            cleanPhone = "359" + cleanPhone.substring(1);
        }

        // Basic Auth header
        String credentials = Base64.getEncoder()
                .encodeToString((systemId + ":" + password).getBytes());

        // JSON body според NTH документацията
        Map<String, Object> body = new HashMap<>();
        body.put("channels", List.of("WHATSAPP"));
        body.put("requestId", UUID.randomUUID().toString());
        body.put("destinations", List.of(Map.of("phoneNumber", cleanPhone)));

        Map<String, Object> whatsAppText = new HashMap<>();
        whatsAppText.put("type", "text");
        whatsAppText.put("content", Map.of("text", message));

        Map<String, Object> whatsApp = new HashMap<>();
        whatsApp.put("sender", senderId);
        whatsApp.put("messageData", whatsAppText);

        body.put("whatsApp", whatsApp);

        log.info("Sending WhatsApp to: {}", cleanPhone);

        String response = restClient.post()
                .uri(apiUrl + "/v1/omni-channel/message")
                .header("Authorization", "Basic " + credentials)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response1) -> {
                    String respBody = new String(response1.getBody().readAllBytes());
                    log.error("WhatsApp API Error: {} - {}", response1.getStatusCode(), respBody);
                    throw new RuntimeException("WhatsApp API Error: " + response1.getStatusCode() + " " + respBody);
                })
                .body(String.class);

        log.info("WhatsApp Response: {}", response); // ← добави това
        return response;
    }

    public String sendWhatsAppTemplate(String toPhone, String orderNumber) {
        String cleanPhone = toPhone.replaceAll("[^0-9]", "");
        if (cleanPhone.startsWith("0")) {
            cleanPhone = "359" + cleanPhone.substring(1);
        }

        String credentials = Base64.getEncoder()
                .encodeToString((systemId + ":" + password).getBytes());

        // 1. Template обект
        Map<String, Object> template = new HashMap<>();
        template.put("name", "romania");
        template.put("language", "ro");

        // 2. Content обвива template-а
        Map<String, Object> content = new HashMap<>();
        content.put("template", template);  // ← ключовата разлика

        // 3. messageData
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("type", "template");
        messageData.put("content", content);

        // 4. whatsApp обект
        Map<String, Object> whatsApp = new HashMap<>();
        whatsApp.put("sender", senderId);
        whatsApp.put("messageData", messageData);

        // 5. Тяло на заявката
        Map<String, Object> body = new HashMap<>();
        body.put("channels", List.of("WHATSAPP"));
        body.put("requestId", UUID.randomUUID().toString());
        body.put("destinations", List.of(Map.of("phoneNumber", cleanPhone)));
        body.put("whatsApp", whatsApp);

        log.info("Sending WhatsApp template 'Romania' to: {}", cleanPhone);

        String response = restClient.post()
                .uri(apiUrl + "/v1/omni-channel/message")
                .header("Authorization", "Basic " + credentials)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, resp) -> {
                    String respBody = new String(resp.getBody().readAllBytes());
                    log.error("WhatsApp API Error: {} - {}", resp.getStatusCode(), respBody);
                    throw new RuntimeException("WhatsApp API Error: " + resp.getStatusCode() + " " + respBody);
                })
                .body(String.class);

        log.info("WhatsApp Template Response: {}", response);
        return response;
    }

    public String checkStatus(String messageId) {
        String credentials = Base64.getEncoder()
                .encodeToString((systemId + ":" + password).getBytes());

        String response = restClient.get()
                .uri(apiUrl + "/v1/omni-channel/message/" + messageId + "/status")
                .header("Authorization", "Basic " + credentials)
                .retrieve()
                .body(String.class);

        log.info("Status: {}", response);
        return response;
    }

    public String listTemplates() {
        String credentials = Base64.getEncoder()
                .encodeToString((systemId + ":" + password).getBytes());

        String response = restClient.get()
                .uri(apiUrl + "/v1/omni-channel/whatsapp/senders/" + senderId + "/templates")
                .header("Authorization", "Basic " + credentials)
                .retrieve()
                .body(String.class);

        log.info("Templates: {}", response);
        System.out.println(response);
        return response;
    }


}