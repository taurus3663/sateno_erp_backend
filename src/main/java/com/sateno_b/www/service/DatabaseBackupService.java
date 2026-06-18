package com.sateno_b.www.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@Log4j2
public class DatabaseBackupService {

    @Value("${backup.path:/var/backups/sateno_erp}")
    private String backupPath;

    @Value("${backup.keep-days:30}")
    private int keepDays;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Sofia")
    public void performBackup() {
        try {
            Path backupDir = Path.of(backupPath);
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
            }

            String filename = "sateno_erp_" + LocalDate.now() + ".dump";
            Path backupFile = backupDir.resolve(filename);

            ProcessBuilder pb = new ProcessBuilder(
                    "pg_dump",
                    "-h", "localhost",
                    "-p", "5432",
                    "-U", "postgres",
                    "-d", "sateno_erp",
                    "-F", "c",
                    "-f", backupFile.toString()
            );
            pb.environment().put("PGPASSWORD", dbPassword);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Бекъп успешен: {}", backupFile);
                deleteOldBackups(backupDir);
            } else {
                log.error("Бекъп неуспешен (exit {}): {}", exitCode, output);
            }
        } catch (Exception e) {
            log.error("Грешка при бекъп: {}", e.getMessage(), e);
        }
    }

    private void deleteOldBackups(Path dir) throws IOException {
        Instant cutoff = Instant.now().minus(keepDays, ChronoUnit.DAYS);
        try (var stream = Files.list(dir)) {
            stream
                    .filter(f -> f.getFileName().toString().startsWith("sateno_erp_")
                            && f.getFileName().toString().endsWith(".dump"))
                    .filter(f -> {
                        try {
                            return Files.getLastModifiedTime(f).toInstant().isBefore(cutoff);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(f -> {
                        try {
                            Files.delete(f);
                            log.info("Изтрит стар бекъп: {}", f);
                        } catch (IOException e) {
                            log.warn("Неуспешно изтриване: {}", f);
                        }
                    });
        }
    }
}
