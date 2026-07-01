package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.AiRecommendationEntity;
import com.sateno_b.www.model.enums.RecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AiRecommendationRepository extends JpaRepository<AiRecommendationEntity, Long> {

    List<AiRecommendationEntity> findTop100ByOrderByCreateTimeDesc();

    List<AiRecommendationEntity> findByStatusOrderByLeadScoreDesc(RecommendationStatus status);

    /** За да не дублираме чернова за същия клиент, генерирана скоро (напр. в последното пускане). */
    boolean existsByCustomerIdAndStatusAndCreateTimeAfter(
            Long customerId, RecommendationStatus status, Instant after);
}
