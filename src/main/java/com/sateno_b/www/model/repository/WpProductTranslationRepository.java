package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.LanguageEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpProductEntity;
import com.sateno_b.www.model.entity.WpProductTranslationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpProductTranslationRepository extends JpaRepository<WpProductTranslationEntity, Long> {

    // Spring Data JPA автоматично разбира, че трябва да филтрира по трите обекта
    Optional<WpProductTranslationEntity> findByProductAndLanguage(
            WpProductEntity product,
            LanguageEntity language
    );
}
