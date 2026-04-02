package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.CustomerEntity;
import com.sateno_b.www.model.entity.EmailLogEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WpOrderRepository extends JpaRepository<WpOrderEntity, Long>, JpaSpecificationExecutor<WpOrderEntity> {
//    @Query("SELECT o FROM WpOrderEntity o WHERE " +
//            "(:status IS NULL OR o.status = :status) AND " +
//    "(:phone IS NULL OR o.customer.phone = :phone) AND" +
//            "(:customer IS NULL OR :customer = '' OR (" +
//            " LOWER(o.customer.firstName) LIKE LOWER(CONCAT('%', :customer, '%')) OR " +
//            " LOWER(o.customer.lastName) LIKE LOWER(CONCAT('%', :customer, '%')) OR " +
//            " o.customer.phone LIKE CONCAT('%', :customer, '%')))")
//    Page<WpOrderEntity> findWithFilters(@Param("status") OrderStatus status, @Param("phone") String phone,@Param("customer") String customerNameORPhone, Pageable pageable);

    @Query("SELECT o FROM WpOrderEntity o " +
            "JOIN FETCH o.customer " + // Изтегляме клиента веднага
            "JOIN FETCH o.site " +     // Изтегляме сайта веднага
            "WHERE (:status IS NULL OR o.status = :status) " +
            "AND (:phone IS NULL OR o.customer.phone = :phone) " +
            "AND (:customer IS NULL OR :customer = '' OR " +
            "   (o.customer.firstName ILIKE %:customer% OR " + // ILIKE е Case-insensitive и по-бърз в Postgres
            "    o.customer.lastName ILIKE %:customer% OR " +
            "    o.customer.phone LIKE %:customer%))")
    Page<WpOrderEntity> findWithFilters(
            @Param("status") OrderStatus status,
            @Param("phone") String phone,
            @Param("customer") String customer,
            Pageable pageable
    );

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

    @Query("SELECT o.customer.id, COUNT(o) FROM WpOrderEntity o WHERE o.customer IN :customers GROUP BY o.customer.id")
    List<Object[]> countByCustomersBatch(@Param("customers") List<CustomerEntity> customers);

    Boolean existsByWpOrderId(Long id);
    Optional<WpOrderEntity> findByEmailsContaining(EmailLogEntity emailLog);

    Optional<WpOrderEntity> findByWpOrderId(Long wpOrderId);

    List<WpOrderEntity> findAllByCourierTypeAndStatus(CourierType courierType, OrderStatus status);

    @Query("SELECT o.status, COUNT(o) FROM WpOrderEntity o GROUP BY o.status")
    List<Object[]> countOrdersByStatus();


}
