package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.enums.CourierType;
import jakarta.persistence.*;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "courier")
public class CourierSettingsEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private CourierType courierType;

    @Column
    private String name;

    @Column
    private String username;
    @Column
    private String password;

    @Column
    private String apiKey;
    @Column
    private String apiSecret;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private SiteEntity site;

    @Column(nullable = false)
    private boolean active = false;
}
