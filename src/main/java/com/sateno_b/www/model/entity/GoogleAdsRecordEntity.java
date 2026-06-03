package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.time.*;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Table(name = "google_ads_record")
@Entity
@Data
public class GoogleAdsRecordEntity extends BaseEntity {

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
    private GoogleAdsEntity ad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_name_id")
    private GoogleAdsCampaignName campaignName;

    @Column(nullable = false)
    private Instant recordedAt;

    public void setRecordedAt(LocalDate date, int hour) {
        this.recordedAt = LocalDateTime.of(date, LocalTime.of(hour, 0))
                .toInstant(ZoneOffset.UTC);
    }
}
