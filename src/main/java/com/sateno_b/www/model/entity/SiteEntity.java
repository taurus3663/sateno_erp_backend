package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "site")
public class SiteEntity extends BaseEntity {

    @Column
    private String name;

    @Column
    private String url;

    @Column
    private String consumerKey;

    @Column
    private String consumerSecret;

    @ManyToOne
    @JoinColumn(name = "currency_id")
    private CurrencyEntity currency;

    @ManyToOne
    @JoinColumn(name = "language_id")
    private LanguageEntity language;

    @Column(nullable = false)
    private boolean active = false;

    @Column()
    private String orderCreateApiKey;

//    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
//    private List<CourierSettingsEntity> couriers;


}
