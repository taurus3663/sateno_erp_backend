package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.listeners.GoogleAdsEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Table(name = "google_ads")
@Entity
@Data
@EntityListeners(GoogleAdsEntityListener.class)
public class GoogleAdsEntity extends BaseEntity {

    @Column
    private String name;

    @Column(columnDefinition = "boolean default true")
    private boolean active;

    @Column
    private String clientId;

    @Column
    private String clientSecret;

    @Column
    private String refreshToken;

    @Column
    private String loginCustomerId;

    @Column
    private String developerToken;

    @Column
    private String timeZone;
}
