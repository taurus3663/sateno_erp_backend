package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpProductRepository extends JpaRepository<WpProductEntity,Long> {

        // 1. Намира продукта по SKU от сайт конфигурацията
        @Query("SELECT p FROM WpProductEntity p JOIN p.siteConfigs sc WHERE sc.sku = :sku")
        Optional<WpProductEntity> findBySkuInConfigs(@Param("sku") String sku);

        // 2. Намира продукта по SKU и конкретен сайт (Ключово за синхронизацията)
        @Query("SELECT p FROM WpProductEntity p JOIN p.siteConfigs sc WHERE sc.sku = :sku AND sc.site.id = :siteId")
        Optional<WpProductEntity> findBySkuAndSite(@Param("sku") String sku, @Param("siteId") Long siteId);

        // 3. Зареждане на пълния обект за Angular DTO-то (добавяме и siteConfigs)
        @Query("SELECT p FROM WpProductEntity p " +
                "LEFT JOIN FETCH p.translations " +
                "LEFT JOIN FETCH p.siteConfigs " + // Зареждаме и цените/ску за всички сайтове
                "LEFT JOIN FETCH p.images " +
                "LEFT JOIN FETCH p.addonConfig " +
                "WHERE p.id = :id")
        Optional<WpProductEntity> findByIdWithDetails(@Param("id") Long id);
    }
