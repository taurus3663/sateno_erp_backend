package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class PaoIdValueDto {
    private String key; // _pao_ids
    private List<PaoIdValueValueDto> value;
    private Long id; // 123131 -> required to find addon on db
}
