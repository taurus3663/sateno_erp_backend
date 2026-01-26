package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpProductImageSiteMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpProductImageSiteMappingRepository extends JpaRepository<WpProductImageSiteMappingEntity, Long> {

    // Проверява дали вече сме индексирали тази медия от конкретния сайт
    boolean existsByWpMediaIdAndSite(Long wpMediaId, SiteEntity site);

    // Опционално: Намиране на самия мапинг, ако ви потрябва
    Optional<WpProductImageSiteMappingEntity> findByWpMediaIdAndSite(Long wpMediaId, SiteEntity site);
}
