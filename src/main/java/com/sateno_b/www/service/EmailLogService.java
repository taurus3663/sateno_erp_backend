package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.EmailLogEntity;
import com.sateno_b.www.model.repository.EmailLogRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class EmailLogService {

    private final EmailLogRepository emailLogRepository;

    @Transactional
    public boolean processConfirmationOrder(String key) {
        Optional<EmailLogEntity> confirmKey = emailLogRepository.findByPrivateConfirmKey(key);
        boolean success = false;

        if(confirmKey.isPresent()) {
            EmailLogEntity emailLogEntity = confirmKey.get();
            if(!emailLogEntity.isConfirmed()) {
                emailLogEntity.setConfirmed(true);
                emailLogRepository.save(emailLogEntity);
                success = true;
            }
        }
        return success;
    }
}
