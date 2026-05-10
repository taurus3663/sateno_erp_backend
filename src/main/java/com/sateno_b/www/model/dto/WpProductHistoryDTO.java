package com.sateno_b.www.model.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class WpProductHistoryDTO {

    private Long id;
    private Instant createTime;
    private long quantity;
    private String reason;
    private Long orderId;
    private Long productId;
    private String productSku;
    private Long wpOrderId;
    private Long oldQuantity;
    private Long newQuantity;
    private String changerName;

}
