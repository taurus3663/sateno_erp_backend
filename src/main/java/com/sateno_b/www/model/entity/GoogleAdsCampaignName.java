package com.sateno_b.www.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Table(name = "google_ads_campaign_name")
@Entity
@Data
public class GoogleAdsCampaignName extends BaseEntity {

    @Column
    private String name;
}
