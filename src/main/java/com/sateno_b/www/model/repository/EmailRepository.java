package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.EmailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailRepository extends JpaRepository<EmailEntity, Long> {
    List<EmailEntity> findAllByActiveTrue();
}
