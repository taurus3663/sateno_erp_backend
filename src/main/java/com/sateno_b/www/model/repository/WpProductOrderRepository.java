package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpProductOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WpProductOrderRepository extends JpaRepository<WpProductOrder, Long>, JpaSpecificationExecutor<WpProductOrder> {

    List<WpProductOrder> findAllByCategoryId(Long categoryId, Pageable pageable);
}
