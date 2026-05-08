package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.DiscountPhoneDTO;
import com.sateno_b.www.model.interfaces.BaseController;
import com.sateno_b.www.service.DiscountPhoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/discount")
public class DiscountController implements BaseController<DiscountPhoneDTO, Long> {

    private final DiscountPhoneService discountPhoneService;


    @PostMapping("/phone_save")
    public boolean saveNewPhone(@RequestBody DiscountPhoneDTO discountPhoneDTO) {
      return discountPhoneService.saveNewPhone(discountPhoneDTO);
    }

    @Override
    public ResponseEntity<Page<DiscountPhoneDTO>> list(Pageable pageable) {
        Page<DiscountPhoneDTO> list = discountPhoneService.list(pageable);
        return ResponseEntity.ok(list);
    }

    @Override
    public ResponseEntity<DiscountPhoneDTO> get(Long aLong) {
        return null;
    }

    @Override
    public ResponseEntity<DiscountPhoneDTO> save(DiscountPhoneDTO dto) {
        return null;
    }

    @Override
    public boolean delete(Long aLong) {
        return false;
    }
}
