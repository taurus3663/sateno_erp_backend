package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.CustomerIntelligenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerIntelligenceRepository extends JpaRepository<CustomerIntelligenceEntity, Long> {

    Optional<CustomerIntelligenceEntity> findByCustomerId(Long customerId);

    /** Топ лийдове по Lead Score (за таблото/препоръките). */
    List<CustomerIntelligenceEntity> findTop50ByOrderByLeadScoreDesc();

    /** Лийдове със score над праг (за генериране на препоръки; обхватът е сменяем). */
    List<CustomerIntelligenceEntity> findByLeadScoreGreaterThanEqualOrderByLeadScoreDesc(int minScore);
}
