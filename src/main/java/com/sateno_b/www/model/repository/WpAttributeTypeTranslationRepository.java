package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpAttributeTypeTranslationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WpAttributeTypeTranslationRepository extends JpaRepository<WpAttributeTypeTranslationEntity, Long> {
}
