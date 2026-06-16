package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpAttributeValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WpAttributeValueRepository extends JpaRepository<WpAttributeValueEntity, Long> {
    List<WpAttributeValueEntity> findAllByAttributeTypeId(Long typeId);

    @Query("SELECT v FROM WpAttributeValueEntity v JOIN v.translations t " +
           "WHERE v.attributeType.slug = :typeSlug AND t.language.code = :langCode AND t.label = :label")
    Optional<WpAttributeValueEntity> findByTypeSlugAndLabelAndLang(
            @Param("typeSlug") String typeSlug,
            @Param("label") String label,
            @Param("langCode") String langCode
    );
}
