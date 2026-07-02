package com.sateno_b.www.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Кошница по сесия за списъците „Продукти в количка (без каса)" и „Каси без въведени данни".
 * Един ред = една сесия/клиент с продуктите в количката му (със снимки), стойност и час.
 * Захранва се от live_session (снимка на количката), затова работи и за минали периоди.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveBasketView {

    private Long id;
    private Long siteId;
    private String device;
    private BigDecimal cartValue;
    private String currency;
    private int productsCount;
    private String lastActivity;
    private List<LiveSnapshotDto.AbandonedView.AbandonedItem> items;
}
