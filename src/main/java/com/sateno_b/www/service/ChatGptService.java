package com.sateno_b.www.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.FileOutputStream;

@Service
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
