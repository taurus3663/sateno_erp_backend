package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.MetaAdsCampaignName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetaAdsCampaignNameRepository extends JpaRepository<MetaAdsCampaignName,Long> {
}
