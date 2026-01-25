package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpBrandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpBrandRepository extends JpaRepository<WpBrandEntity, Long> {
    Optional<WpBrandEntity> findBySlug(String slug);
}
