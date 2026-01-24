package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WpProductImageDto {

    private String localSrc;

    private List<WpProductImageSiteMappingDto> siteMappings = new ArrayList<>();
}
