package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.GoogleAdsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GoogleAdsRepository extends JpaRepository<GoogleAdsEntity, Long> {
}
