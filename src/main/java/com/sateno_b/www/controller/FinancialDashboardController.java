package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.FinancialCardDto;
import com.sateno_b.www.model.dto.FinancialDashboardDto;
import com.sateno_b.www.service.FinancialDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Финансов дашборд — основен финансов екран на ERP-то.
 *
 * GET /financial/dashboard?timeZone=Europe/Sofia
 * Връща трите карти (днес / вчера / последни 7 дни) с показатели,
 * текущ и предходен период за сравнение. Изисква автентикация (JWT).
 */
@Slf4j
@RestController
@RequestMapping("/financial")
@RequiredArgsConstructor
public class FinancialDashboardController {

    private final FinancialDashboardService financialDashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<FinancialDashboardDto> getDashboard(
            @RequestParam(required = false) String timeZone,
            @RequestParam(required = false) List<Long> siteIds) {
        return ResponseEntity.ok(financialDashboardService.getDashboard(timeZone, siteIds));
    }

    /**
     * Карта за произволен период.
     * GET /financial/period?from=2026-06-01&to=2026-06-08&timeZone=Europe/Sofia&siteIds=6
     */
    @GetMapping("/period")
    public ResponseEntity<FinancialCardDto> getPeriod(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String timeZone,
            @RequestParam(required = false) List<Long> siteIds) {
        return ResponseEntity.ok(
                financialDashboardService.getPeriodCard(LocalDate.parse(from), LocalDate.parse(to), timeZone, siteIds));
    }
}
