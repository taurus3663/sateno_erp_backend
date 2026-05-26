package com.sateno_b.www.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Table(name = "meta_ads")
@Entity
@Data
public class MetaAdsEntity extends BaseEntity {

    private String adAccountId;
    private String accessToken;
    private String name;

}
