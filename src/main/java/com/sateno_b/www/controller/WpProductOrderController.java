package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.WpProductOrderDTO;
import com.sateno_b.www.model.interfaces.BaseController;
import com.sateno_b.www.service.WpProductOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/wp_product_order")
public class WpProductOrderController implements BaseController<WpProductOrderDTO, Long> {

    private final WpProductOrderService wpProductOrderService;

    @Override
    public ResponseEntity<Page<WpProductOrderDTO>> list(Pageable pageable, Map<String, String> params) {

        Page<WpProductOrderDTO> all = wpProductOrderService.getAll(pageable, params);

        return ResponseEntity.ok(all);
    }

    @Override
    public ResponseEntity<WpProductOrderDTO> get(Long aLong) {
        return null;
    }

    @Override
    public ResponseEntity<WpProductOrderDTO> save(WpProductOrderDTO dto) {
        return null;
    }

    @Override
    public boolean delete(Long aLong) {
        return false;
    }
}
