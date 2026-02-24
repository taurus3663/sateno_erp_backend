package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.entity.data.CourierConfig;
import com.sateno_b.www.model.entity.data.CourierContractDetails;
import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.enums.CourierType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(nullable = false)
    private boolean office = false;
    @Column(nullable = false)
    private boolean address = false;
    @Column(nullable = false)
    private boolean locker = false;

    @Column(nullable = false)
    private boolean defaultCourier = false;

//    @Enumerated(EnumType.STRING)
//    private CourierShipmentType courierShipmentType;

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

    @Column
    private Integer sortOrder;
    @Column(nullable = false)
    private Double freeShippingPriceMax = 0D;

    @Column(nullable = false)
    private Boolean freeShippingPriceMaxBol = false;
    @Column(nullable = false)
    private Boolean autoShippingPrice = false;
    @Column
    private Double fixedShippingPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private CourierContractDetails courierContractDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private CourierConfig config;

}
