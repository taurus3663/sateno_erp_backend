package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.listeners.MetaAdsEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Table(name = "meta_ads")
@Entity
@Data
@EntityListeners(MetaAdsEntityListener.class)
public class MetaAdsEntity extends BaseEntity {

    @Column
    private String adAccountId;
    @Column
    private String accessToken;
    @Column
    private String name;
    @Column(columnDefinition = "boolean default true")
    private boolean active;

}
