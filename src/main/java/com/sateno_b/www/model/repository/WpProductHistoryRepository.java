package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.entity.WpProductEntity;
import com.sateno_b.www.model.entity.WpProductHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WpProductHistoryRepository extends JpaRepository<WpProductHistoryEntity, Long> {
    Optional<WpProductHistoryEntity> findByProductSku(String productSku);

    Optional<WpProductHistoryEntity> findFirstByProductSkuAndOrder(String productSku, WpOrderEntity order);
}
