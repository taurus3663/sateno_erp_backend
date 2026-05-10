package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.WpProductHistoryDTO;
import com.sateno_b.www.model.entity.UserEntity;
import com.sateno_b.www.model.entity.WpProductHistoryEntity;
import com.sateno_b.www.model.repository.WpProductHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class WpProductHistoryService {

    private final WpProductHistoryRepository wpProductHistoryRepository;

    public Page<WpProductHistoryDTO> getAll(Pageable pageable, String name_sku) {



        Page<WpProductHistoryEntity> all = wpProductHistoryRepository.findAll(pageable);
        Page<WpProductHistoryDTO> map = all.map(entity -> {
            WpProductHistoryDTO dto = new  WpProductHistoryDTO();
            dto.setId(entity.getId());
            dto.setProductId(entity.getProduct().getId());
            dto.setReason(entity.getReason());
            dto.setCreateTime(entity.getCreateTime());
            dto.setProductSku(entity.getProduct().getSku());
            dto.setQuantity(entity.getQuantity());
            dto.setNewQuantity(entity.getNewQuantity());
            dto.setOldQuantity(entity.getOldQuantity());
            dto.setQuantity(entity.getQuantity());
            if(entity.getOrder() != null) {
                dto.setOrderId(entity.getOrder().getId());
                dto.setWpOrderId(entity.getOrder().getWpOrderId());
            }

            UserEntity user = entity.getUser();
            dto.setChangerName(user != null? user.getUsername(): "System");

            return dto;
        });
        return map;
    }
}
