package com.sateno_b.www.service.ai;

import com.sateno_b.www.service.ChatGptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Резервен AI доставчик — OpenAI, чрез съществуващия {@link ChatGptService}.
 *
 * Не дублира инфраструктура — просто обвива вече работещия ChatGpt повик зад общия
 * {@link AiProvider} интерфейс, за да служи като fallback, ако Claude не е конфигуриран.
 */
@Component
@Order(2) // резервен
@RequiredArgsConstructor
@Log4j2
public class OpenAiProvider implements AiProvider {

    private final ChatGptService chatGptService;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        // ChatGptService.translateText(text, instruction) слага instruction като system,
        // а text като user — точно каквото ни трябва.
        return chatGptService.translateText(userPrompt, systemPrompt);
    }
}
