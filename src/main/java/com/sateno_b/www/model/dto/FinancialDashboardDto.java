package com.sateno_b.www.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Кореновият обект, който дашбордът връща към фронтенда.
 *
 * Засега съдържа трите фиксирани карти (днес / вчера / последни 7 дни),
 * но е оставено и поле {@code extraCards} за бъдещи периоди
 * (текущ месец, минал месец, тази година, произволен период и т.н.),
 * така че разширяването да не изисква пренаписване.
 */
@Data
@NoArgsConstructor
public class FinancialDashboardDto {

    private FinancialCardDto today;
    private FinancialCardDto yesterday;
    private FinancialCardDto dayBeforeYesterday;
    private FinancialCardDto last7Days;
    private FinancialCardDto lastMonth;

    /** Допълнителни периоди за бъдещи версии (текущ месец, произволен период...) */
    private List<FinancialCardDto> extraCards = new ArrayList<>();

    /** Валута, в която са всички парични стойности */
    private String currency = "EUR";

    /** Момент на изчисление (UTC) */
    private Instant generatedAt;
}
