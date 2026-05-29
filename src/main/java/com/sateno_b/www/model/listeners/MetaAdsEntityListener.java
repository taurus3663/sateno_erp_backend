package com.sateno_b.www.model.listeners;

import jakarta.persistence.PostPersist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class MetaAdsEntityListener {

    private final org.springframework.beans.factory.ObjectProvider<com.sateno_b.www.service.MetaAdsService> metaAdsServiceProvider;

    public MetaAdsEntityListener(org.springframework.beans.factory.ObjectProvider<com.sateno_b.www.service.MetaAdsService> metaAdsServiceProvider) {
        this.metaAdsServiceProvider = metaAdsServiceProvider;
    }

    @PostPersist
    public void postPersist(com.sateno_b.www.model.entity.MetaAdsEntity metaAdsEntity) {
        if (metaAdsEntity.isActive()) {
            metaAdsServiceProvider.ifAvailable(service -> {
                CompletableFuture.runAsync(() -> {
                    try {
                        service.triggerBackfillForNewAccount(metaAdsEntity);
                    } catch (Exception e) {
                        log.error("Грешка при първоначален backfill за акаунт {}: {}", metaAdsEntity.getName(), e.getMessage());
                    }
                });
            });
        }
    }

}
