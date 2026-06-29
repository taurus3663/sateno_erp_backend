package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.ProductAnalysisItemDto;
import com.sateno_b.www.service.ProductAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/product_analysis")
@RequiredArgsConstructor
public class ProductAnalysisController {

    private final ProductAnalysisService productAnalysisService;

    /**
     * GET /product_analysis?from=2026-01-01&to=2026-06-29&dMax=0&cMax=1&bMax=2&timeZone=Europe/Sofia
     */
    @GetMapping
    public ResponseEntity<List<ProductAnalysisItemDto>> analyze(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "0") int dMax,
            @RequestParam(defaultValue = "1") int cMax,
            @RequestParam(defaultValue = "2") int bMax,
            @RequestParam(required = false) String timeZone) {

        List<ProductAnalysisItemDto> result = productAnalysisService.analyze(
                LocalDate.parse(from), LocalDate.parse(to), timeZone, dMax, cMax, bMax);
        return ResponseEntity.ok(result);
    }
}
