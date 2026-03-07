package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpProductHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WpProductHistoryRepository extends JpaRepository<WpProductHistoryEntity, Long> {
}
