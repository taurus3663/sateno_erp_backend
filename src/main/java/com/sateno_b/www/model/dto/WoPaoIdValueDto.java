package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sateno_b.www.model.entity.data.PaoIdValueValue;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WoPaoIdValueDto {
    private String key; // _pao_ids
//    private List<WoPaoIdValueValueDto> value;
    private Object value;
    private Long id; // 123131 -> required to find addon on db
}
