package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.LanguageEntity;
import com.sateno_b.www.model.entity.WpAddonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpAddonRepository extends JpaRepository<WpAddonEntity, Long> {

    Optional<WpAddonEntity> findBySlug(String slug);

    @Query("SELECT a FROM WpAddonEntity a JOIN a.translations t WHERE t.name = :name AND t.language = :lang")
    Optional<WpAddonEntity> findByNameAndLanguage(
            @Param("name") String name,
            @Param("lang") LanguageEntity lang
    );

}
