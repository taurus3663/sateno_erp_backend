package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sateno_b.www.model.entity.data.OrderShippingAndBilling;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.enums.PaymentMethod;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WoOrderDto {

    private Long id;
    private OrderStatus status;
    private String currency;
    private String total;

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("payment_method")
    private PaymentMethod paymentMethod;

    @JsonProperty("payment_method_title")
    private String paymentMethodTitle;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("customer_ip_address")
    private String customerIpAddress;

    @JsonProperty("customer_user_agent")
    private String customerUserAgent;

    @JsonProperty("customer_note")
    private String customerNote;

    @JsonProperty("currency_symbol")
    private String currencySymbol;

    private OrderShippingAndBilling billing;
    private OrderShippingAndBilling shipping;

    @JsonProperty("line_items")
    private List<WoOrderLineItemDto> lineItems;

    @JsonProperty("date_created")
    private LocalDateTime dateCreated;
}
