package com.sateno_b.www.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {


    // Базов път до всички качвания
    private static final String ROOT_PATH = System.getProperty("user.home") + "/uploads/sateno_pim";
   // Папка за временни файлове: /home/taurus/uploads/sateno_pim/temp
    private static final String TEMP_DIR = ROOT_PATH + "/temp";
    // Папка за продукти: /home/taurus/uploads/sateno_pim/products
    private static final String PRODUCT_DIR = ROOT_PATH + "/products";

    public static String saveProductImage(byte[] imageBytes, Long productId, Long wpMediaId, String originalUrl) {
        try {
            Path productPath = Paths.get(PRODUCT_DIR, productId.toString());
            if (!Files.exists(productPath)) {
                Files.createDirectories(productPath);
            }

            String extension = getExtension(originalUrl);
            String fileName = "wp_id_" + wpMediaId + "_" + System.currentTimeMillis() + extension;
            Path filePath = productPath.resolve(fileName);

            Files.write(filePath, imageBytes);

            // ВРЪЩАМЕ ВИРТУАЛЕН ПЪТ ЗА БД:
            // Вместо filePath.toString(), връщаме това, което Webconfig очаква
            return "/media/products/" + productId + "/" + fileName;

        } catch (IOException e) {
            log.error("Грешка при запис на файл за продукт {}: {}", productId, e.getMessage());
            return null;
        }
    }

    private static String getExtension(String url) {
        if (url == null || !url.contains(".")) return ".jpg";
        return url.substring(url.lastIndexOf(".")).toLowerCase();
    }

    public String moveTempImageToProductDir(String tempFileName, Long productId) {
        try {
            // 1. ИЗПОЛЗВАЙ АБСОЛЮТНИЯ TEMP_DIR
            Path sourcePath = Paths.get(TEMP_DIR, tempFileName);

            if (!Files.exists(sourcePath)) {
                log.error("Временният файл НЕ съществува на адрес: {}", sourcePath.toAbsolutePath());
                return null;
            }

            // 2. ИЗПОЛЗВАЙ PRODUCT_DIR ЗА ЦЕЛ (не TEMP_DIR)
            Path targetDir = Paths.get(PRODUCT_DIR, productId.toString());
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // Генерираме име
            String extension = getExtension(tempFileName);
            String finalFileName = "prod_" + UUID.randomUUID().toString() + extension;
            Path targetPath = targetDir.resolve(finalFileName);

            // 3. ПРЕМЕСТВАНЕ
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Файлът е преместен успешно: {} -> {}", sourcePath, targetPath);

            // Връщаме виртуалния път за WebConfig
            return "/media/products/" + productId + "/" + finalFileName;

        } catch (IOException e) {
            log.error("Грешка при местене на временен файл: {}", e.getMessage());
            return null;
        }
    }

    public void deleteProductImage(String virtualPath) {
        try {
            if (virtualPath == null || !virtualPath.startsWith("/media/")) return;

            // Превръщаме /media/products/1/file.jpg -> /home/taurus/uploads/sateno_pim/products/1/file.jpg
            String relativePath = virtualPath.replace("/media/", "");
            Path filePath = Paths.get(ROOT_PATH, relativePath);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Физическият файл е изтрит: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Грешка при изтриване на файл {}: {}", virtualPath, e.getMessage());
        }
    }

    public byte[] getImageBytes(String virtualPath) {
        try {
            if (virtualPath == null || !virtualPath.startsWith("/media/")) {
                log.error("Невалиден виртуален път: {}", virtualPath);
                return null;
            }

            // Превръщаме виртуалния път (/media/products/1/file.jpg)
            // във физически (/home/user/uploads/sateno_pim/products/1/file.jpg)
            String relativePath = virtualPath.replace("/media/", "");
            Path filePath = Paths.get(ROOT_PATH, relativePath);

            if (Files.exists(filePath)) {
                return Files.readAllBytes(filePath);
            } else {
                log.error("Файлът не съществува на физически адрес: {}", filePath);
                return null;
            }
        } catch (IOException e) {
            log.error("Грешка при четене на байтове от файл {}: {}", virtualPath, e.getMessage());
            return null;
        }
    }
}
