package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class WooAddonDto {

    private String name;
    private String type;
    private List<WooAddonOptionsDto> options;
}
