package com.sateno_b.www.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Една карта от дашборда (напр. "Днес", "Вчера", "Последните 7 дни").
 * Съдържа показателите за текущия период и за съответния предходен период,
 * за да може фронтендът да изчисли процентна разлика и цветова индикация.
 */
@Data
@NoArgsConstructor
public class FinancialCardDto {

    /** Ключ на периода: TODAY | YESTERDAY | LAST_7_DAYS (използва се и за бъдещи периоди) */
    private String period;

    /** Начало на текущия период (UTC) */
    private Instant from;

    /** Край на текущия период (UTC) */
    private Instant to;

    /** Показатели за текущия период */
    private FinancialMetricsDto current;

    /** Показатели за съответния предходен период (за сравнение) */
    private FinancialMetricsDto previous;
}
