package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {
    @Query(value = "SELECT * FROM customer c WHERE c.phone = :phone ORDER BY c.create_time DESC LIMIT 1", nativeQuery = true)
    Optional<CustomerEntity> findByPhone(String phone);

    @Query("SELECT c FROM CustomerEntity c WHERE c.phone LIKE %:suffix ORDER BY c.id DESC")
    List<CustomerEntity> findByPhoneSuffix(@Param("suffix") String suffix);
}
