package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Table(name = "meta_ads_record")
@Entity
@Data
public class MetaAdsRecordEntity extends BaseEntity {

    private String campaignName;
    private Double spend;
    private Integer clicks;
    private Integer impressions;
    private Double cpc;
    private Double cpm;
    private Double ctr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ad_id")
    private MetaAdsEntity ad;

}
