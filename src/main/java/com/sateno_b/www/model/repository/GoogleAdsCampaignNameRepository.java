package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.GoogleAdsCampaignName;
import com.sateno_b.www.model.entity.GoogleAdsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoogleAdsCampaignNameRepository extends JpaRepository<GoogleAdsCampaignName, Long> {
    Optional<GoogleAdsCampaignName> findByName(String campaignNameStr);
}
