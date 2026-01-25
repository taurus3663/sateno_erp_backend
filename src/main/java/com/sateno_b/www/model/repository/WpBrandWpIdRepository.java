package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpBrandWpIdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpBrandWpIdRepository extends JpaRepository<WpBrandWpIdEntity, Long> {
    Optional<WpBrandWpIdEntity> findBySiteAndWpId(SiteEntity site, Long wpId);
}
