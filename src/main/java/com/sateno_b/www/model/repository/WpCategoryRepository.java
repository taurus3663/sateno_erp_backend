package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpCategoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WpCategoryRepository extends JpaRepository<WpCategoryEntity, Long> {
    Optional<WpCategoryEntity> findBySlug(String slug);
    Page<WpCategoryEntity> findByParentIsNull(Pageable pageable);
    // За децата на конкретен родител
    List<WpCategoryEntity> findByParentId(Long parentId);
    // Проверка дали категорията има деца (за да сетнем leaf флага)
    boolean existsByParentId(Long parentId);
}
