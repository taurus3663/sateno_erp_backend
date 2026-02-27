package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.UserSignalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSignalRepository  extends JpaRepository<UserSignalEntity, Long> {
    List<UserSignalEntity> findByCustomerId(Long customerId);
}
