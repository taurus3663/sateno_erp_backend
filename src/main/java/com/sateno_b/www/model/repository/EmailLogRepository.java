package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.dto.EmailLogDto;
import com.sateno_b.www.model.entity.EmailLogEntity;
import com.sateno_b.www.model.enums.EmailDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLogEntity, Long> {
    Page<EmailLogEntity> findAllByDirectionIs(EmailDirection direction, Pageable pageable);
    Optional<EmailLogEntity> findByPrivateSeenKey(String privateSeenKey);
    Optional<EmailLogEntity> findByPrivateConfirmKey(String privateConfirmKey);
}
