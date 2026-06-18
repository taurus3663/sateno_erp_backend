package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CourierSettingsCache {

    private final CourierSettingsRepository courierSettingsRepository;

    @Cacheable("courierSettingsMap")
    public Map<String, CourierSettingsEntity> getMap() {
        return courierSettingsRepository.findAll().stream()
                .filter(c -> c.isActive() && c.isDefaultCourier() && c.getSite() != null)
                .collect(Collectors.toMap(
                        c -> c.getSite().getId() + "_" + c.getCourierType().name(),
                        c -> c,
                        (a, b) -> a
                ));
    }

    @CacheEvict(value = "courierSettingsMap", allEntries = true)
    public void evict() {}
}
