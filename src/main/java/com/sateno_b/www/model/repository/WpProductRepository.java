package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpProductRepository extends JpaRepository<WpProductEntity,Long> {
    // Намира продукта по SKU от неговите преводи
    @Query("SELECT p FROM WpProductEntity p JOIN p.translations t WHERE t.sku = :sku")
    Optional<WpProductEntity> findBySkuInTranslations(@Param("sku") String sku);

    // Още по-добре: Намира продукта по SKU и конкретен сайт
    // (защото един и същ SKU може да съществува в различни сайтове за различни продукти в редки случаи)
    @Query("SELECT p FROM WpProductEntity p JOIN p.translations t WHERE t.sku = :sku AND t.site.id = :siteId")
    Optional<WpProductEntity> findBySkuAndSite(@Param("sku") String sku, @Param("siteId") Long siteId);
}
