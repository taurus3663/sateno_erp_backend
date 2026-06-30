package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class WooAttributeDto {
    private Long id;
    private String name;
    private String slug;
    private List<String> options;
}
