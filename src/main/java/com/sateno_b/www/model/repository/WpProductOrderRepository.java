package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpProductOrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WpProductOrderRepository extends JpaRepository<WpProductOrderEntity, Long>, JpaSpecificationExecutor<WpProductOrderEntity> {

    Page<WpProductOrderEntity> findAllByCategoryId(Long categoryId, Pageable pageable);

    Optional<WpProductOrderEntity> findByCategoryId(Long categoryId);
}
