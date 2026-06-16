package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpAttributeTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpAttributeTypeRepository extends JpaRepository<WpAttributeTypeEntity, Long> {
    Optional<WpAttributeTypeEntity> findBySlug(String slug);
}
