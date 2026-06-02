package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpCategoryEntity;
import com.sateno_b.www.model.entity.WpCategorySiteMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpCategorySiteMappingRepository extends JpaRepository<WpCategorySiteMappingEntity, Long> {
    // Намираш mapping-а за конкретна категория в конкретен сайт
    Optional<WpCategorySiteMappingEntity> findByWpCategoryAndSite(WpCategoryEntity category, SiteEntity site);
    Optional<WpCategorySiteMappingEntity> findBySiteIdAndWpId(Long siteId, Long wpId);
    Optional<WpCategorySiteMappingEntity> findByWpCategoryAndSiteId(WpCategoryEntity wpCategory, Long siteId);
    // Проверка дали категорията вече е свързана с този сайт
    boolean existsByWpCategoryAndSite(WpCategoryEntity category, SiteEntity site);

    void deleteAllBySite(SiteEntity site);
}
