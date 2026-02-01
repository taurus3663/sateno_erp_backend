package com.sateno_b.www.model.entity.data;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PaoIdValue {

    private String key; // _pao_ids
    private List<PaoIdValueValue> value;
    private Long id; // 123131 -> required to find addon on db
}
