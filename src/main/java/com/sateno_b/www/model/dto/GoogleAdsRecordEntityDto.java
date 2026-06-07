package com.sateno_b.www.model.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class GoogleAdsRecordEntityDto {

    private Double spend;
    private Integer clicks;
    private Integer impressions;
    private Double cpc;
    private Double cpm;
    private Double ctr;
    private Instant recordedAt;
}
