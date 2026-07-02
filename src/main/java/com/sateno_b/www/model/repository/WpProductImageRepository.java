package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WpProductImageRepository extends JpaRepository<WpProductImageEntity, Long> {

    /**
     * Основната локална снимка (local_src) по SKU (малки букви) — за обогатяване на редовете на
     * поръчките с ТЕКУЩАТА снимка на продукта (вместо остарял уловен WooCommerce URL).
     * Връща двойки [sku(lower), localSrc].
     */
    @Query("SELECT lower(i.product.sku), i.localSrc FROM WpProductImageEntity i " +
           "WHERE i.isPrimary = true AND i.localSrc IS NOT NULL AND i.localSrc <> '' " +
           "AND lower(i.product.sku) IN :skus")
    List<Object[]> findPrimaryLocalSrcBySkus(@Param("skus") Collection<String> skus);
}
