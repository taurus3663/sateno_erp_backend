package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.WpProductHistoryDTO;
import com.sateno_b.www.model.entity.UserEntity;
import com.sateno_b.www.model.entity.WpProductEntity;
import com.sateno_b.www.model.entity.WpProductHistoryEntity;
import com.sateno_b.www.model.entity.WpProductTranslationEntity;
import com.sateno_b.www.model.repository.WpProductHistoryRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class WpProductHistoryService {

    private final WpProductHistoryRepository wpProductHistoryRepository;

    public Page<WpProductHistoryDTO> getAll(Pageable pageable, String searchTerm) {

        Specification<WpProductHistoryEntity> spec = (root, query, cb) -> {
            if (searchTerm == null || searchTerm.isEmpty()) {
                return null;
            }

            String pattern = "%" + searchTerm.toLowerCase() + "%";

            // 1. Създаваме Subquery, което работи върху WpProductEntity
            Subquery<Long> productSubquery = query.subquery(Long.class);
            Root<WpProductEntity> productRoot = productSubquery.from(WpProductEntity.class);

            // Join-ваме преводите, за да търсим по име
            Join<WpProductEntity, WpProductTranslationEntity> translationJoin = productRoot.join("translations");

            // Предикат за името ИЛИ SKU-то вътре в продукта
            Predicate productMatches = cb.or(
                    cb.like(cb.lower(translationJoin.get("name")), pattern),
                    cb.like(cb.lower(productRoot.get("sku")), pattern)
            );

            // Селектираме само ID-тата на продуктите
            productSubquery.select(productRoot.get("id")).where(productMatches);

            // 2. Основната заявка: Търсим в историята, където продуктът е сред намерените
            // Приемаме, че в HistoryEntity имаш: @ManyToOne WpProductEntity product;
            return root.get("product").get("id").in(productSubquery);
        };

        Page<WpProductHistoryEntity> all = wpProductHistoryRepository.findAll(spec, pageable);
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
