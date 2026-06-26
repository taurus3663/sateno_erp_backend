package com.sateno_b.www.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Финансови показатели за един период.
 * Всички парични стойности са в основната валута на дашборда (EUR).
 *
 * За да добавиш нов KPI: добави поле тук + попълни го във
 * {@link com.sateno_b.www.service.FinancialDashboardService}. Нищо друго не се променя.
 */
@Data
@NoArgsConstructor
public class FinancialMetricsDto {

    /** Брой поръчки */
    private long orders;

    /** Брой продадени продукти (сума от количествата) */
    private long productsSold;

    /** Общ оборот */
    private double revenue;

    /** Средна стойност на една поръчка = Оборот / Брой поръчки */
    private double avgOrderValue;

    /** Средна стойност на продаден продукт = Оборот / Брой продадени продукти */
    private double avgProductValue;

    /** Себестойност = Σ(покупна цена + транспортен разход) за всички продадени продукти */
    private double cogs;

    /** Разход за доставки (само поръчки, при които доставката е за сметка на фирмата) */
    private double shippingCost;

    /** Разход за реклама (Meta + Google) */
    private double adSpend;

    /** CPA — разход за придобиване на поръчка = Разход за реклама / Брой поръчки (null, ако няма поръчки) */
    private Double cpa;

    /** CPIS — разход за реклама на продаден артикул = Разход за реклама / Продадени продукти (null, ако няма продукти) */
    private Double cpis;

    /** Брутна печалба = Оборот - Себестойност */
    private double grossProfit;

    /** Чиста печалба = Оборот - Себестойност - Доставка(фирма) - Реклама */
    private double netProfit;

    /** ROAS = Оборот / Разход за реклама (null, когато няма рекламен разход) */
    private Double roas;

    /** Марж на чистата печалба = Чиста печалба / Оборот * 100 */
    private double margin;
}
