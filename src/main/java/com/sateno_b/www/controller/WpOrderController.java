package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.WpOrderDto;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.repository.WpOrderRepository;
import com.sateno_b.www.service.WpOrderService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wp_order")
@RequiredArgsConstructor
public class WpOrderController {

    private final WpOrderRepository wpOrderRepository;
    private final ModelMapper modelMapper;
    private final WpOrderService wpOrderService;

    @GetMapping("/list")
    public ResponseEntity<Page<WpOrderDto>> getAll(Pageable pageable) {
        Page<WpOrderEntity> wpOrderEntities = wpOrderRepository.findAll(pageable);
        Page<WpOrderDto> wpOrderDtos =wpOrderEntities.map(mapper -> modelMapper.map(mapper, WpOrderDto.class));

        return ResponseEntity.ok(wpOrderDtos);
    }

    @PostMapping("/sync/{siteId}")
    public void syncWpOrder(@PathVariable Long siteId){
            wpOrderService.syncOrderToDB(siteId);
    }
}
