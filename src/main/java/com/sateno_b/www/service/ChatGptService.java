package com.sateno_b.www.service;

import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;

import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.util.List;

@Service
@Slf4j
public class ChatGptService {

    private final ChatClient chatClient;

    public ChatGptService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String translateText(String text, String instruction) {
        return chatClient.prompt()
                .system(instruction)
                .user(text)
                .call()
                .content();
    }

    /**
     * НОВ МЕТОД: Генериране на съдържание въз основа на текст и списък от снимки
     * @param prompt Инструкцията и контекста (текст)
     * @param imagePaths Списък с физически пътища до снимките на сървъра
     */
    public String generateWithImages(String prompt, List<String> imagePaths) {
       try {
           return chatClient.prompt()
                   .user(u -> {
                       u.text(prompt); // Добавяме текстовия промпт
                       for (String path : imagePaths) {
                           // Създаваме ресурс от физическия път
                           FileSystemResource imageResource = new FileSystemResource(Paths.get(path));

                           // Определяме MimeType (автоматично или ръчно)
                           var mimeType = path.toLowerCase().endsWith(".png") ?
                                   MimeTypeUtils.IMAGE_PNG : MimeTypeUtils.IMAGE_JPEG;

                           // Добавяме медията към потребителското съобщение
                           u.media(mimeType, imageResource);
                       }
                   })
                   .call()
                   .content();
       } catch (Exception e) {
           e.printStackTrace();
           log.error("AI Generation failed: {}", e.getMessage());
           return "Възникна грешка при генерирането на текста.";
       }
    }


//    // МЕТОД 2: За писане на имейли
//    public String writeCustomerEmail(String orderId, String status) {
//        return chatClient.prompt()
//                .system("Ти си любезен асистент поддръжка.") // Съвсем различна роля
//                .user("Напиши имейл за поръчка " + orderId + " със статус " + status)
//                .call()
//                .content();
//    }
//
//    // МЕТОД 3: Универсален (ти си му казваш всичко)
//    public String askAnything(String systemInstruction, String userMessage) {
//        return chatClient.prompt()
//                .system(systemInstruction)
//                .user(userMessage)
//                .call()
//                .content();
//    }


//    @Value("${spring.ai.openai.api-key}")
//    private String apiKey;
//    // НОВ МЕТОД: За теглене на файла, който ти трябва
//    public void downloadFile(String fileId, String outputFileName) {
//        RestClient restClient = RestClient.builder()
//                .baseUrl("https://api.openai.com/v1")
//                .defaultHeader("Authorization", "Bearer " + apiKey)
//                .build();
//
//        try {
//            byte[] fileBytes = restClient.get()
//                    .uri("/files/{fileId}/content", fileId)
//                    .retrieve()
//                    .body(byte[].class);
//
//            if (fileBytes != null) {
//                try (FileOutputStream fos = new FileOutputStream(outputFileName)) {
//                    fos.write(fileBytes);
//                    System.out.println("✅ Файлът " + fileId + " е записан като: " + outputFileName);
//                }
//            }
//        } catch (Exception e) {
//            System.err.println("❌ Грешка при теглене на файл: " + e.getMessage());
//        }
//    }


}
