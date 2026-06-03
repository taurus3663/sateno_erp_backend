package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.GoogleAdsCampaignName;
import com.sateno_b.www.model.entity.GoogleAdsEntity;
import com.sateno_b.www.model.entity.GoogleAdsRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GoogleAdsRecordRepository extends JpaRepository<GoogleAdsRecordEntity, Long> {

    @Query("SELECT DISTINCT r.campaignName FROM GoogleAdsRecordEntity r WHERE r.ad = :ad")
    List<GoogleAdsCampaignName> findDistinctCampaignsByAd(@Param("ad")GoogleAdsEntity ad);
}
