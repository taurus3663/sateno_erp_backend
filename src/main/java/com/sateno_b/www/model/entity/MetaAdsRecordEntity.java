package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Table(name = "meta_ads_record")
@Entity
@Data
public class MetaAdsRecordEntity extends BaseEntity {

    @Column
    private Double spend;
    @Column
    private Integer clicks;
    @Column
    private Integer impressions;
    @Column
    private Double cpc;
    @Column
    private Double cpm;
    @Column
    private Double ctr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ad_id")
    private MetaAdsEntity ad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_name_id")
    private MetaAdsCampaignName campaignName;

    @Column(nullable = false)
    private Instant recordedAt; // конкретният час: 2026-05-29T12:00:00Z


}
