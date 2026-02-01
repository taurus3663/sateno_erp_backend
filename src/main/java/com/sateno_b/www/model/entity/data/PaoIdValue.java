package com.sateno_b.www.model.entity.data;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaoIdValue {

    private String key; // "molya izberete razmer..."
    private String value; // "bez lastik 240x260cm"
    private String id; // 123131 -> reuired to find addon on db

    private BigDecimal rawPrice;
    private BigDecimal rawTotalPrice;
}
