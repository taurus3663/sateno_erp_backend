package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.entity.data.OrderShippingAndBilling;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class WpOrderDto {

    private Long id;
    private Long wpOrderId;
    private OrderStatus status;
    private CustomerDto customer;
    private OrderShippingAndBilling billing;
    private OrderShippingAndBilling shipping;
    private SiteDto site;
    private PaymentMethod paymentMethod;
    private String transactionId;
    private String customerIp;
    private String customerAgent;
    private List<OrderLineItem> orderLine;
    private BigDecimal totalPrice;
    private String currency;
    private String currencySymbol;
    private Instant createTime;
    private Instant updateTime;

}
