package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.SyncStatusDto;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class FullSyncService {

    private final WpBrandService wpBrandService;
    private final WpCategoryService wpCategoryService;
    private final WpProductService wpProductService;
    private final WpAttributeSyncService wpAttributeSyncService;
    private final WpOrderService wpOrderService;
    private final SiteRepository siteRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final ZoneId ZONE = ZoneId.of("Europe/Sofia");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Singleton state — survives page refresh, resets on server restart
    private volatile String step = "IDLE";
    private volatile Long runningSiteId = null;
    private volatile String runningSiteName = null;
    private volatile Instant startTime = null;
    private volatile String errorMessage = null;
    private final List<String> logs = Collections.synchronizedList(new ArrayList<>());

    public boolean isRunning() {
        return "BRANDS".equals(step) || "CATEGORIES".equals(step) || "PRODUCTS".equals(step) || "ATTRIBUTES".equals(step) || "ORDERS".equals(step);
    }

    public SyncStatusDto getStatus() {
        SyncStatusDto dto = new SyncStatusDto();
        dto.setStep(step);
        dto.setSiteId(runningSiteId);
        dto.setSiteName(runningSiteName);
        dto.setLogs(new ArrayList<>(logs));
        dto.setErrorMessage(errorMessage);
        if (startTime != null) {
            dto.setElapsedSeconds(Duration.between(startTime, Instant.now()).getSeconds());
        }
        return dto;
    }

    public void startSync(Long siteId, boolean importOrders) {
        if (isRunning()) {
            throw new IllegalStateException("Синхронизацията вече е в ход — изчакайте да приключи");
        }

        SiteEntity site = siteRepository.findById(siteId)
                .orElseThrow(() -> new RuntimeException("Сайтът не е намерен"));

        // Reset state
        step = "BRANDS";
        runningSiteId = siteId;
        runningSiteName = site.getName() != null ? site.getName() : site.getUrl();
        startTime = Instant.now();
        errorMessage = null;
        logs.clear();

        addLog("🚀 Стартиране на синхронизация от сайт \"" + runningSiteName + "\"");
        addLog("ℹ️  Всички съществуващи записи ще бъдат обновени, нови ще се добавят — без дублиране");
        if (importOrders) addLog("ℹ️  Поръчките също ще бъдат импортирани");

        CompletableFuture.runAsync(() -> {
            try {
                // Step 1: Brands
                Instant t0 = Instant.now();
                addLog("⏳ Стъпка 1/3 — Синхронизиране на марки...");
                wpBrandService.syncBrandsToDB(siteId);
                addLog("✅ Марките са синхронизирани (" + elapsed(t0) + "с)");

                // Step 2: Categories
                Instant t1 = Instant.now();
                step = "CATEGORIES";
                addLog("⏳ Стъпка 2/3 — Синхронизиране на категории...");
                wpCategoryService.syncCategoriesToDatabase(siteId);
                addLog("✅ Категориите са синхронизирани (" + elapsed(t1) + "с)");

                // Step 3: Products + images
                Instant t2 = Instant.now();
                step = "PRODUCTS";
                addLog("⏳ Стъпка 3/4 — Синхронизиране на продукти и снимки (може да отнеме няколко минути)...");
                wpProductService.syncProductsToDB(siteId);
                addLog("✅ Продуктите и снимките са синхронизирани (" + elapsed(t2) + "с)");

                // Step 4: Attributes
                Instant t3 = Instant.now();
                step = "ATTRIBUTES";
                addLog("⏳ Стъпка 4/4 — Синхронизиране на атрибути и свързване с продукти...");
                wpAttributeSyncService.pullFromSite(siteId);
                addLog("✅ Атрибутите са синхронизирани (" + elapsed(t3) + "с)");

                // Step 5 (optional): Orders
                if (importOrders) {
                    Instant t4 = Instant.now();
                    step = "ORDERS";
                    addLog("⏳ Стъпка 5/5 — Импортиране на поръчки (може да отнеме известно време)...");
                    wpOrderService.syncOrderToDB(siteId);
                    addLog("✅ Поръчките са импортирани (" + elapsed(t4) + "с)");
                }

                step = "DONE";
                addLog("🎉 Синхронизацията завърши успешно! Общо: " + elapsed(startTime) + "с");

            } catch (Exception e) {
                step = "ERROR";
                errorMessage = e.getMessage();
                addLog("❌ Грешка: " + e.getMessage());
                log.error("Грешка при пълна синхронизация от сайт {}: {}", siteId, e.getMessage(), e);
            }
        });
    }

    private void addLog(String message) {
        String time = LocalTime.now(ZONE).format(TIME_FMT);
        logs.add("[" + time + "] " + message);
        messagingTemplate.convertAndSend("/topic/sync_status", getStatus());
    }

    private String elapsed(Instant from) {
        return String.format("%.1f", Duration.between(from, Instant.now()).toMillis() / 1000.0);
    }
}
