package com.sateno_b.www.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatGptService {

    private static final String TYPE = "CHATGPT";

    private final AppSettingsService appSettingsService;

    private ChatClient buildClient() {
        Map<String, String> config = appSettingsService.getConfig(TYPE);
        String apiKey = config.getOrDefault("apiKey", "");
        String model = config.getOrDefault("model", "gpt-4o");
        double temperature = Double.parseDouble(config.getOrDefault("temperature", "1.0"));

        OpenAiApi openAiApi = new OpenAiApi(apiKey);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();
        return ChatClient.create(new OpenAiChatModel(openAiApi, options));
    }

    public String translateText(String text, String instruction) {
        return buildClient().prompt()
                .system(instruction)
                .user(text)
                .call()
                .content();
    }

    public String generateWithImages(String prompt, List<String> imagePaths) {
        try {
            return buildClient().prompt()
                    .user(u -> {
                        u.text(prompt);
                        for (String path : imagePaths) {
                            FileSystemResource imageResource = new FileSystemResource(Paths.get(path));
                            var mimeType = path.toLowerCase().endsWith(".png") ?
                                    MimeTypeUtils.IMAGE_PNG : MimeTypeUtils.IMAGE_JPEG;
                            u.media(mimeType, imageResource);
                        }
                    })
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI Generation failed: {}", e.getMessage());
            return "Възникна грешка при генерирането на текста.";
        }
    }
}
