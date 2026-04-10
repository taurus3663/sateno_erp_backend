package com.sateno_b.www.model.listeners;

import com.sateno_b.www.model.entity.WpCategoryEntity;
import com.sateno_b.www.service.WpCategoryAsyncService;
import jakarta.persistence.PostPersist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WpCategoryEntityListener {

    private final ObjectProvider<WpCategoryAsyncService> asyncService;

    public WpCategoryEntityListener(ObjectProvider<WpCategoryAsyncService> asyncService) {
        this.asyncService = asyncService;
    }

    @PostPersist
    public void postPersist(WpCategoryEntity wpCategoryEntity) {

        asyncService.ifAvailable( service -> {
            try {
                service.asyncPostPersist(wpCategoryEntity);
            } catch (InterruptedException e) {
                log.error("Категория с ID {} има проблем {}", wpCategoryEntity.getId(), e.getMessage());
            }
        });

    }
}
