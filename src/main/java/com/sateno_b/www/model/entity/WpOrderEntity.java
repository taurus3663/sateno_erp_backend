package com.sateno_b.www.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sateno_b.www.model.dto.EmailLogDto;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.entity.data.OrderShippingAndBilling;
import com.sateno_b.www.model.entity.data.ShippingLines;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.enums.PaymentMethod;
import com.sateno_b.www.model.listeners.WpOrderEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@EntityListeners(WpOrderEntityListener.class)
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wp_order")
public class WpOrderEntity extends BaseEntity {

    @Column
    private Long parentId;

    @Column(unique = true)
    private Long wpOrderId;

    @OneToMany
    private List<WpProductSiteConfigEntity> wpProductSiteConfigEntityList;

    @Enumerated(EnumType.ORDINAL)
    private OrderStatus status;

    @Column
    private Instant wpOrderTime;

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

    @Column
    private String customerIp;

    @Column(columnDefinition = "TEXT")
    private String customerAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<OrderLineItem> orderLine = new ArrayList<>(); // line_items

    @Column
    private BigDecimal totalPrice;

    @Column
    private String currency;
    @Column
    private String currency_symbol;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ShippingLines> shippingLines = new ArrayList<>();

    @Column
    private String wayBillUrl;
    @Column
    private Long wayBillShipmentNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> parcelIds = new ArrayList<>();

    @Enumerated(EnumType.ORDINAL)
    private CourierType courierType;
    @Column
    private Long courierId;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private List<EmailLogEntity> emails = new ArrayList<>();

    @Column()
    private String comment;

    @Transient
    private WpOrderEntity snapshot; // Пазим снимка на целия обект

    @PostLoad
    public void createSnapshot() {
        // Правим повърхностно копие на най-важните полета
        this.snapshot = new WpOrderEntity();
        this.snapshot.setStatus(this.status);
//        this.snapshot.setTotalPrice(this.totalPrice);
//        this.snapshot.setCourierType(this.courierType);
//        this.snapshot.setComment(this.comment);
    }
}
