package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.WpProductHistoryDTO;
import com.sateno_b.www.model.interfaces.BaseController;
import com.sateno_b.www.service.WpProductHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/wp_product_history")
public class WpProductHistoryController implements BaseController<WpProductHistoryDTO, Long> {

    private final WpProductHistoryService wpProductHistoryService;

//    @Override
//    public ResponseEntity<Page<WpProductHistoryDTO>> list(Pageable pageable) {
//        Page<WpProductHistoryDTO> all = wpProductHistoryService.getAll(pageable, name_sku);
//        return ResponseEntity.ok(all);
//    }

    @Override
    public ResponseEntity<Page<WpProductHistoryDTO>> list(Pageable pageable, Map<String, String> params) {
        String name_sku = params.get("name_sku");

        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by("id").descending()
            );
        }

        Page<WpProductHistoryDTO> all = wpProductHistoryService.getAll(pageable, name_sku);
        return ResponseEntity.ok(all);
    }

    @Override
    public ResponseEntity<WpProductHistoryDTO> get(Long aLong) {
        return null;
    }

    @Override
    public ResponseEntity<WpProductHistoryDTO> save(WpProductHistoryDTO dto) {
        return null;
    }

    @Override
    public boolean delete(Long aLong) {
        return false;
    }
}
