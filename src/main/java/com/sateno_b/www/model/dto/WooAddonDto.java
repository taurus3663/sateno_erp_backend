package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.WooAddonOptionsDto;
import lombok.Data;

import java.util.List;

@Data
public class WooAddonDto {

    private String name;
    private List<WooAddonOptionsDto> options;
}
