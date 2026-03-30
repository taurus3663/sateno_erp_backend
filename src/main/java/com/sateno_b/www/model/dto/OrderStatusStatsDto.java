package com.sateno_b.www.model.dto;

import com.sateno_b.www.model.enums.OrderStatus;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class OrderStatusStatsDto {
    private Map<OrderStatus, Long> orderStatusMap = new HashMap<>();
}
