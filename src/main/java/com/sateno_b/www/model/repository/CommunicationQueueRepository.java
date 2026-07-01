package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.CommunicationQueueEntity;
import com.sateno_b.www.model.enums.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunicationQueueRepository extends JpaRepository<CommunicationQueueEntity, Long> {

    List<CommunicationQueueEntity> findByStatusOrderByCreateTimeAsc(QueueStatus status);

    List<CommunicationQueueEntity> findTop100ByOrderByCreateTimeDesc();

    long countByStatus(QueueStatus status);

    boolean existsByRecommendationId(Long recommendationId);
}
