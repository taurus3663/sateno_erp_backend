package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.MetaAdsCampaignName;
import com.sateno_b.www.model.entity.MetaAdsEntity;
import com.sateno_b.www.model.entity.MetaAdsRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MetaAdsRecordRepository extends JpaRepository<MetaAdsRecordEntity, Long> {

//    @Query("SELECT MAX(r.createTime) FROM MetaAdsRecordEntity r WHERE r.ad = :ad")
//    Optional<Instant> findLatestCreatedAt(@Param("ad") MetaAdsEntity ad);

    @Query("SELECT MAX(r.recordedAt) FROM MetaAdsRecordEntity r WHERE r.ad = :ad")
    Optional<Instant> findLatestRecordedAt(@Param("ad") MetaAdsEntity ad);

//    boolean existsByAdAndCreateTime(MetaAdsEntity ad, Instant date);

    boolean existsByAdAndRecordedAtAndCampaignName(
            MetaAdsEntity ad, Instant recordedAt, MetaAdsCampaignName campaignName
    );

    // В MetaAdsRecordRepository.java
    @Query("SELECT DISTINCT r.campaignName FROM MetaAdsRecordEntity r WHERE r.ad = :ad")
    List<MetaAdsCampaignName> findDistinctCampaignsByAd(@Param("ad") MetaAdsEntity ad);


    @Query("SELECT r FROM MetaAdsRecordEntity r " +
            "WHERE r.campaignName = :campaign " +
            "AND r.recordedAt >= :start AND r.recordedAt <= :end " +
            "ORDER BY r.recordedAt ASC")
    List<MetaAdsRecordEntity> findByCampaignAndDateRange(
            @Param("campaign") MetaAdsCampaignName campaign,
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}


