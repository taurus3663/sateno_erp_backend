package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class WpBrandDto {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
}
