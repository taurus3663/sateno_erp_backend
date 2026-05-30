package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.MetaAdsCampaignName;
import com.sateno_b.www.model.entity.MetaAdsEntity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

import java.time.Instant;

@Data
public class MetaAdsRecordEntityDto {

    private Double spend;
    private Integer clicks;
    private Integer impressions;
    private Double cpc;
    private Double cpm;
    private Double ctr;
    private Instant recordedAt;
}
