package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.LanguageEntity;
import com.sateno_b.www.model.entity.WpAddonEntity;
import com.sateno_b.www.model.entity.WpAddonTranslationEntity;
import com.sateno_b.www.model.entity.WpAddonValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpAddonTranslationRepository extends JpaRepository<WpAddonTranslationEntity, Long> {

    // WpAddonRepository.java
    @Query("SELECT a FROM WpAddonEntity a JOIN a.translations t WHERE t.name = :name AND t.language = :lang")
    Optional<WpAddonEntity> findByNameAndLanguage(@Param("name") String name, @Param("lang") LanguageEntity lang);

    // WpAddonValueRepository.java
    @Query("SELECT v FROM WpAddonValueEntity v JOIN v.translations t WHERE t.label = :label AND t.language = :lang")
    Optional<WpAddonValueEntity> findByLabelAndLanguage(@Param("label") String label, @Param("lang") LanguageEntity lang);
}
