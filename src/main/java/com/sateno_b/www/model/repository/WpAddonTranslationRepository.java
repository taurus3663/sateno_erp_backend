package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpAddonTranslationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WpAddonTranslationRepository extends JpaRepository<WpAddonTranslationEntity, Long> {
}
