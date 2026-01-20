package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpCategoryTranslationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpCategoryTranslationRepository extends JpaRepository<WpCategoryTranslationEntity, Long> {

    // Търсим превод по ID на категорията и ID на езика
    Optional<WpCategoryTranslationEntity> findByWpCategoryIdAndLanguageId(Long categoryId, Long languageId);
//    Optional<WpCategoryTranslationEntity> findByWpCategory(Long categoryId, Long languageId);
}
