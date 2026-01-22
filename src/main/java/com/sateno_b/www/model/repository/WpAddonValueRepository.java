package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpAddonValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpAddonValueRepository extends JpaRepository<WpAddonValueEntity, Long> {

    // Търсим стойност (напр. 'green') вътре в конкретна група (напр. 'color')
//    Optional<WpAddonValueEntity> findBySlugAndGroupId(String slug, Long groupId);
}
