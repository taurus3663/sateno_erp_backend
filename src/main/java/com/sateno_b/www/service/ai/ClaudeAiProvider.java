package com.sateno_b.www.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Основен AI доставчик — Anthropic (Claude).
 *
 * Реализиран е с директен REST повик към Messages API, а НЕ през spring-ai starter.
 * Причина: проектът вече ползва spring-ai-openai starter; добавянето на втори ChatModel
 * би счупило авто-конфигурацията на съществуващия {@code ChatGptService}
 * (ChatClient.Builder се създава само при точно един ChatModel). Директният повик е
 * самостоятелен, не пипа OpenAI настройката и дава пълен контрол.
 *
 * Ключът се чете от env/property {@code anthropic.api-key} (правило #9 — НЕ в кода/git).
 * Ако ключ липсва → {@link #isAvailable()} връща false и router-ът минава на резервния.
 */
@Component
@Order(1) // основен
@Log4j2
public class ClaudeAiProvider implements AiProvider {

    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final RestClient restClient;

    public ClaudeAiProvider(
            @Value("${anthropic.api-key:}") String apiKey,
            @Value("${anthropic.model:claude-sonnet-5}") String model,
            @Value("${anthropic.max-tokens:1024}") int maxTokens,
            @Value("${anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${anthropic.version:2023-06-01}") String anthropicVersion
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.maxTokens = maxTokens;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("anthropic-version", anthropicVersion)
                .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isBlank();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            throw new IllegalStateException("Claude API ключ липсва (anthropic.api-key)");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", systemPrompt == null ? "" : systemPrompt,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", userPrompt == null ? "" : userPrompt
                ))
        );

        JsonNode resp = restClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (resp == null || !resp.has("content") || !resp.get("content").isArray()
                || resp.get("content").isEmpty()) {
            log.warn("Claude: празен/неочакван отговор: {}", resp);
            throw new IllegalStateException("Празен отговор от Claude API");
        }
        // content е масив от блокове; взимаме първия текстов блок.
        for (JsonNode block : resp.get("content")) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText();
            }
        }
        return resp.get("content").get(0).path("text").asText();
    }
}
