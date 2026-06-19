package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.AppSettingsEntity;
import com.sateno_b.www.model.repository.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppSettingsService {

    private final AppSettingsRepository appSettingsRepository;

    @Cacheable(value = "appSettings", key = "#type")
    public Map<String, String> getConfig(String type) {
        return appSettingsRepository.findByType(type)
                .map(AppSettingsEntity::getConfig)
                .orElse(new HashMap<>());
    }

    @CacheEvict(value = "appSettings", key = "#type")
    public void saveConfig(String type, String name, Map<String, String> config) {
        AppSettingsEntity entity = appSettingsRepository.findByType(type)
                .orElseGet(() -> {
                    AppSettingsEntity e = new AppSettingsEntity();
                    e.setType(type);
                    return e;
                });
        entity.setName(name);
        entity.setConfig(config);
        appSettingsRepository.save(entity);
    }
}
