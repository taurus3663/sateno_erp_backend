package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class SyncStatusDto {
    /** IDLE | BRANDS | CATEGORIES | PRODUCTS | DONE | ERROR */
    private String step = "IDLE";
    private Long siteId;
    private String siteName;
    private List<String> logs = List.of();
    private String errorMessage;
    private long elapsedSeconds;
}
