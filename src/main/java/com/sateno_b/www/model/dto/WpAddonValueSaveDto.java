package com.sateno_b.www.model.dto;

import lombok.Data;

@Data
public class WpAddonValueSaveDto {
    private String label; // Напр. "Червен"
    private Long langId;  // Напр. 1 (за Български)
}
