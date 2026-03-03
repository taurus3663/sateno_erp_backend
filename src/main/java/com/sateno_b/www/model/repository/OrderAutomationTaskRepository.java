package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.OrderAutomationTaskEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderAutomationTaskRepository extends JpaRepository<OrderAutomationTaskEntity, Long> {
    List<OrderAutomationTaskEntity> findAllByProcessedFalseAndScheduledTimeBefore(Instant now);

    void deleteAllByOrderAndProcessedFalse(WpOrderEntity order);
}
