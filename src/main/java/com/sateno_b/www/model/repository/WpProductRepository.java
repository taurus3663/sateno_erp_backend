package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpProductEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WpProductRepository extends JpaRepository<WpProductEntity,Long> , JpaSpecificationExecutor<WpProductEntity> {

        // 1. Намира продукта по SKU от сайт конфигурацията
//        @Query("SELECT p FROM WpProductEntity p JOIN p.siteConfigs sc WHERE sc.sku = :sku")
//        Optional<WpProductEntity> findBySkuInConfigs(@Param("sku") String sku);

        // 2. Намира продукта по SKU и конкретен сайт (Ключово за синхронизацията)
        @Query("SELECT p FROM WpProductEntity p JOIN p.siteConfigs sc WHERE p.sku = :sku AND sc.site.id = :siteId")
        Optional<WpProductEntity> findBySkuAndSite(@Param("sku") String sku, @Param("siteId") Long siteId);

        // 3. Зареждане на пълния обект за Angular DTO-то (добавяме и siteConfigs)
//        @Query("SELECT p FROM WpProductEntity p " +
//                "LEFT JOIN FETCH p.addonConfig ac " +
//                "LEFT JOIN FETCH ac.addonValue av " +
//                "LEFT JOIN FETCH av.translations " + // Зареждаме преводите на адоните за Angular
//                "WHERE p.id = :id")
//        Optional<WpProductEntity> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT p FROM WpProductEntity p " +
            "WHERE p.id = :id") // Махаме всички JOIN FETCH за колекции
    Optional<WpProductEntity> findByIdWithDetails(@Param("id") Long id);

        Optional<WpProductEntity> findBySku(String sku);

    @Query("SELECT DISTINCT p FROM WpProductEntity p JOIN p.addonConfig a")
    List<WpProductEntity> findAllWithAddons();


    Optional<WpProductEntity> findFirstByOrderBySkuDesc();


    }
