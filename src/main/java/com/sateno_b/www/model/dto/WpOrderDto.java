package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.entity.data.*;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.enums.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    private List<OrderLineItemDto> orderLine;
    private BigDecimal totalPrice;
    private String currency;
    private String currencySymbol;
    private Instant createTime;
    private Instant updateTime;
    private Instant wpOrderTime;

    private List<OrderLineItemDto> orderLineOtherOrders;
    private Boolean showDuplicateWarning = false;
    private List<Long> ordersToMerge;
    private Long customerOrderCount;
    private List<ShippingLines> shippingLines;
    private String wayBillUrl;
    private Long wayBillShipmentNumber;
    private List<String> parcelIds = new ArrayList<>();
    private CourierType courierType;
    private Long courierId;

    private List<UserSignalDto> signals; //nekorekten

    private boolean confirmed = false;

    private String comment;

    private OrderSavedCourierSettings savedCourierBilling;
    private Double customShippingTotal;
    private List<WpOrderCourierHistory> courierHistory =  new ArrayList<>();
    private BigDecimal totalPriceFCoutier;
}
