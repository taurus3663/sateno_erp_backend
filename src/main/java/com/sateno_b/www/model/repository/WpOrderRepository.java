package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WpOrderRepository extends JpaRepository<WpOrderEntity, Long> {
    @Query("SELECT o FROM WpOrderEntity o WHERE " +
            "(:status IS NULL OR o.status = :status)")
    Page<WpOrderEntity> findWithFilters(@Param("status") OrderStatus status, Pageable pageable);
}
