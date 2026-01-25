package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.WpProductDto;
import com.sateno_b.www.model.entity.WpProductEntity;
import com.sateno_b.www.model.repository.WpProductRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/wp_product")
public class WpProductController {

    private final WpProductRepository wpProductRepository;
    private final ModelMapper modelMapper;

    @GetMapping("/list")
    public ResponseEntity<Page<WpProductDto>> getWpProducts(Pageable pageable) {

        Page<WpProductEntity> dtoPage =  wpProductRepository.findAll(pageable);
        Page<WpProductDto> dtos = dtoPage.map(entity -> modelMapper.map(entity, WpProductDto.class));

        return ResponseEntity.ok(dtos);
    }


    @PostMapping("/sync/{siteId}")
    public void syncWpProducts(@PathVariable Long siteId) {

    }
}
