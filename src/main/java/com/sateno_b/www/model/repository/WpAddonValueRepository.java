package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.LanguageEntity;
import com.sateno_b.www.model.entity.WpAddonValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpAddonValueRepository extends JpaRepository<WpAddonValueEntity, Long> {

    // В WpAddonValueRepository.java
    @Query("SELECT v FROM WpAddonValueEntity v JOIN v.translations t WHERE t.label = :label AND t.language = :lang")
    Optional<WpAddonValueEntity> findByLabelAndLanguage(@Param("label") String label, @Param("lang") LanguageEntity lang);

    // Търсим стойност (напр. 'green') вътре в конкретна група (напр. 'color')
//    Optional<WpAddonValueEntity> findBySlugAndGroupId(String slug, Long groupId);
}
