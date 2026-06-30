package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.LiveProductStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LiveProductStatRepository extends JpaRepository<LiveProductStatEntity, Long> {

    Optional<LiveProductStatEntity> findBySiteIdAndProductWpIdAndStatDate(Long siteId, Long productWpId, LocalDate statDate);

    /** Проекция за агрегирани „най-разглеждани продукти" за период. */
    interface ProductStatAggregate {
        Long getProductWpId();
        String getSku();
        String getName();
        String getImageUrl();
        Long getViews();
        Long getAddToCart();
        Long getCheckoutStarts();
        Long getOrders();
    }

    @Query("select p.productWpId as productWpId, max(p.sku) as sku, max(p.name) as name, max(p.imageUrl) as imageUrl, " +
            "sum(p.views) as views, sum(p.addToCart) as addToCart, sum(p.checkoutStarts) as checkoutStarts, sum(p.orders) as orders " +
            "from LiveProductStatEntity p " +
            "where p.statDate between :from and :to " +
            "group by p.productWpId " +
            "order by sum(p.views) desc")
    List<ProductStatAggregate> aggregateForPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
