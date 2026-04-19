package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.SchemeWpProductDto;
import com.sateno_b.www.model.interfaces.BaseController;
import com.sateno_b.www.model.repository.SchemeWpProductRepository;
import com.sateno_b.www.service.SchemeWpProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/scheme_wp_product")
public class SchemeWpProductController implements BaseController<SchemeWpProductDto, Long> {

    private final SchemeWpProductService schemeWpProductService;


    @Override
    public ResponseEntity<Page<SchemeWpProductDto>> list(Pageable pageable) {
        Page<SchemeWpProductDto> all = schemeWpProductService.getAll(pageable);
        return ResponseEntity.ok(all);
    }

    @Override
    public ResponseEntity<SchemeWpProductDto> get(Long id) {
        SchemeWpProductDto byId = schemeWpProductService.getById(id);
        return ResponseEntity.ok(byId);
    }

    @Override
    public ResponseEntity<SchemeWpProductDto> save(SchemeWpProductDto dto) {
        SchemeWpProductDto save = schemeWpProductService.save(dto);
        return ResponseEntity.ok(save);
    }

    @Override
    public boolean delete(Long id) {
        return schemeWpProductService.deleteById(id);
    }
}
