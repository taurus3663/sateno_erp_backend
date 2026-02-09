package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.CustomerEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WpOrderRepository extends JpaRepository<WpOrderEntity, Long> {
    @Query("SELECT o FROM WpOrderEntity o WHERE " +
            "(:status IS NULL OR o.status = :status) AND " +
    "(:phone IS NULL OR o.customer.phone = :phone)")
    Page<WpOrderEntity> findWithFilters(@Param("status") OrderStatus status, @Param("phone") String phone, Pageable pageable);


    // WpOrderRepository.java
    @Query("SELECT DISTINCT o FROM WpOrderEntity o " +
//            "JOIN FETCH o.orderLine " +
            "WHERE o.customer.phone = :phone " +
            "AND o.status = :status " +
            "AND o.id != :currentId " +
            "AND o.parentId IS NULL") // Не искаме да закачаме поръчки, които вече са били обединени
    List<WpOrderEntity> findDuplicatesWithLines(
            @Param("phone") String phone,
            @Param("status") OrderStatus status,
            @Param("currentId") Long currentId
    );

    long countByCustomer(CustomerEntity customer);

    @Query("SELECT o.customer.id, COUNT(o) FROM WpOrderEntity o WHERE o.customer IN :customers GROUP BY o.customer.id")
    List<Object[]> countByCustomersBatch(@Param("customers") List<CustomerEntity> customers);
}
