package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.WpProductOrderDTO;
import com.sateno_b.www.model.entity.WpProductOrderEntity;
import com.sateno_b.www.model.repository.WpProductOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class WpProductOrderService {

    private final WpProductOrderRepository wpProductOrderRepository;

    public Page<WpProductOrderDTO> getAll(Pageable pageable, Map<String, String> params) {

        String category_id = params.get("category_id");
        if(category_id == null || category_id.equals("")) {
            return Page.empty(pageable);
        }

        Page<WpProductOrderEntity> wp = wpProductOrderRepository.findAllByCategoryId(Long.valueOf(category_id), pageable);

        Page<WpProductOrderDTO> wpDTO = wp.map(entity -> {
            WpProductOrderDTO wpProductOrderDTO = new WpProductOrderDTO();
            return wpProductOrderDTO;
        });


    return wpDTO;
    }
}
