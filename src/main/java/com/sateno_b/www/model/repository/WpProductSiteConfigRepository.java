package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpProductEntity;
import com.sateno_b.www.model.entity.WpProductSiteConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WpProductSiteConfigRepository extends JpaRepository<WpProductSiteConfigEntity, Long> {

    // Намира конфигурацията за конкретен продукт в конкретен сайт
    Optional<WpProductSiteConfigEntity> findByProductAndSite(WpProductEntity product, SiteEntity site);

    // Ако искаш да вземеш всички цени за един продукт (за показване в таблица)
    List<WpProductSiteConfigEntity> findAllByProduct(WpProductEntity product);

    WpProductSiteConfigEntity findBySiteUrl(String siteUrl);

    WpProductSiteConfigEntity findBySiteUrlAndProduct(String siteUrl, WpProductEntity product);
}
