package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.AppSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppSettingsRepository extends JpaRepository<AppSettingsEntity, Long> {
    Optional<AppSettingsEntity> findByType(String type);
}
