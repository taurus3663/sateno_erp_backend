package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.EmailLogEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.repository.EmailLogRepository;
import com.sateno_b.www.model.repository.WpOrderRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class EmailLogService {

    private final EmailLogRepository emailLogRepository;
    private final WpOrderRepository wpOrderRepository;

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

                Optional<WpOrderEntity> byEmailsContaining = wpOrderRepository.findByEmailsContaining(emailLogEntity);
                if(byEmailsContaining.isPresent()) {
                    WpOrderEntity wpOrderEntity = byEmailsContaining.get();
                    wpOrderEntity.setStatus(OrderStatus.APPROVED);
                    wpOrderRepository.save(wpOrderEntity);
                }
            }
        }
        return success;
    }

    @Transactional
    public boolean processCancelOrder(String key) {
        Optional<EmailLogEntity> confirmKey = emailLogRepository.findByPrivateConfirmKey(key);
        boolean success = false;

        if(confirmKey.isPresent()) {
            EmailLogEntity emailLogEntity = confirmKey.get();
            if(!emailLogEntity.isConfirmed()) {
                emailLogEntity.setConfirmed(true);
                emailLogEntity.setCancel(true);
                emailLogRepository.save(emailLogEntity);
                success = true;
                Optional<WpOrderEntity> byEmailsContaining = wpOrderRepository.findByEmailsContaining(emailLogEntity);
                if(byEmailsContaining.isPresent()) {
                    WpOrderEntity wpOrderEntity = byEmailsContaining.get();
                    wpOrderEntity.setStatus(OrderStatus.CANCELLED);
                    wpOrderRepository.save(wpOrderEntity);
                }

            }
        }
        return success;
    }
}
