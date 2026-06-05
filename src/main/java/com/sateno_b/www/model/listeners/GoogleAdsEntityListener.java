package com.sateno_b.www.model.listeners;

import com.sateno_b.www.model.entity.GoogleAdsEntity;
import com.sateno_b.www.service.GoogleAdsService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class GoogleAdsEntityListener {

    private final ObjectProvider<GoogleAdsService> googleAdsServiceProvider;

    public GoogleAdsEntityListener(ObjectProvider<GoogleAdsService> googleAdsServiceProvider) {
        this.googleAdsServiceProvider = googleAdsServiceProvider;
    }

    @PostUpdate
    public void postPersist(GoogleAdsEntity googleAdsEntity) {
        // Проверяваме дали акаунтът е активен
        if (googleAdsEntity.isActive()) {
            googleAdsServiceProvider.ifAvailable(service -> {
                CompletableFuture.runAsync(() -> {
                    try {
                        log.info("🚀 Започва автоматичен backfill за Google Ads акаунт: {}", googleAdsEntity.getName());
                        // Тук трябва да извикаш метод от твоя GoogleAdsService,
                        // който извършва първоначалното извличане на статистика
                        service.triggerBackfillForNewAccount(googleAdsEntity);
                    } catch (Exception e) {
                        log.error("❌ Грешка при първоначален backfill за акаунт {}: {}", googleAdsEntity.getName(), e.getMessage());
                    }
                });
            });
        }
    }
}