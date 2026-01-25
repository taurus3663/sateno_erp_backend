package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class WooBrandDto {

    private Long id; // id of brand on wp site
    private String name;
    private String slug;
}
