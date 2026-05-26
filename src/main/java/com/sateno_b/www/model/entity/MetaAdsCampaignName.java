package com.sateno_b.www.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Table(name = "meta_ads_campaign_name")
@Entity
@Data
public class MetaAdsCampaignName extends BaseEntity {

    private String name;
}
