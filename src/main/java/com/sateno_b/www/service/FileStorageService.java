package com.sateno_b.www.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
public class FileStorageService {

    private static final String uploadDir = System.getProperty("user.home") + "/uploads/sateno_pim/products/";

    public static String saveProductImage(byte[] imageBytes, Long productId, Long wpMediaId, String originalUrl) {
        try {
            // 1. Създаваме папката за продукта: /uploads/sateno_pim/products/{productId}/
            Path productPath = Paths.get(uploadDir, productId.toString());
            if (!Files.exists(productPath)) {
                Files.createDirectories(productPath);
            }

            // 2. Генерираме име на файла (използваме твоята логика с timestamp или WP ID)
            String extension = getExtension(originalUrl);
            String fileName = "wp_id_" + wpMediaId + "_" + System.currentTimeMillis() + extension;
            Path filePath = productPath.resolve(fileName);

            // 3. Записваме байтовете на диска
            Files.write(filePath, imageBytes);

            log.info("Снимката е запазена локално: {}", filePath);

            // Връщаме пътя, който ще се запише в БД (локален път)
            return filePath.toString();
        } catch (IOException e) {
            log.error("Грешка при запис на файл за продукт {}: {}", productId, e.getMessage());
            return null;
        }
    }

    private static String getExtension(String url) {
        if (url == null || !url.contains(".")) return ".jpg";
        return url.substring(url.lastIndexOf(".")).toLowerCase();
    }
}
