package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.MetaAdsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetaAdsRepository extends JpaRepository<MetaAdsEntity, Long> {
    List<MetaAdsEntity> findAllByActiveTrue(boolean active);
}
