package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.GoogleAdsCampaignName;
import com.sateno_b.www.model.entity.GoogleAdsEntity;
import com.sateno_b.www.model.entity.GoogleAdsRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoogleAdsRecordRepository extends JpaRepository<GoogleAdsRecordEntity, Long> {

    @Query("SELECT DISTINCT r.campaignName FROM GoogleAdsRecordEntity r WHERE r.ad = :ad")
    List<GoogleAdsCampaignName> findDistinctCampaignsByAd(@Param("ad")GoogleAdsEntity ad);

    boolean existsGoogleAdsRecordEntityByAd(GoogleAdsEntity ad);

    Optional<GoogleAdsRecordEntity> findByAdAndCampaignNameAndRecordedAt(GoogleAdsEntity account, GoogleAdsCampaignName googleAdsCampaignName, Instant recordedAt);

    @Query("SELECT r FROM GoogleAdsRecordEntity r " +
            "WHERE r.campaignName IN :campaigns " +
            "AND r.recordedAt >= :start AND r.recordedAt <= :end " +
            "ORDER BY r.recordedAt ASC")
    List<GoogleAdsRecordEntity> findByCampaignAndDateRange(List<GoogleAdsCampaignName> campaigns, Instant start, Instant end);

    @Query("SELECT MAX(r.recordedAt) FROM GoogleAdsRecordEntity r WHERE r.ad = :ad")
    Optional<Instant> findLatestRecordedAt(@Param("ad") GoogleAdsEntity ad);
}
