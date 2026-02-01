package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.entity.data.OrderShippingAndBilling;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wp_order")
public class OrderEntity extends BaseEntity {

    @Column(unique = true)
    private Long orderId;

    @OneToMany
    private List<WpProductSiteConfigEntity> wpProductSiteConfigEntityList;

    @Enumerated(EnumType.ORDINAL)
    private OrderStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    private CustomerEntity customer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private OrderShippingAndBilling billing;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private OrderShippingAndBilling shipping;

    @ManyToOne(fetch = FetchType.LAZY)
    private SiteEntity site;

    @Enumerated(EnumType.ORDINAL)
    private PaymentMethod paymentMethod;

    @Column
    private String transactionId;

//    @Column
//    private Instant datePaid;

    @Column
    private String customerIp;

    @Column
    private String customerAgent;




}
