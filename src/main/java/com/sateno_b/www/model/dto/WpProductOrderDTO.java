package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WpProductOrderDTO {
    private List<Long> productIds = new ArrayList<>();
    private Long category;
}
