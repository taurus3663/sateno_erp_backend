package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.SiteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Long> {
    List<SiteEntity> findByActiveTrue();
}
