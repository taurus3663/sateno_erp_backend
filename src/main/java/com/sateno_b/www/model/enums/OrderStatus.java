package com.sateno_b.www.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum OrderStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    ON_HOLD("on-hold"),
    SENT("sent"),
    CANCELLED("cancelled"),
    REFUNDED("refunded"),
    FAILED("failed"),
    TRASH("trash"),
    UNKNOWN("unknown"),
    ABANDONED("abandoned"),
    COMPLETED("completed");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OrderStatus fromValue(String text) {
        for (OrderStatus b : OrderStatus.values()) {
            if (String.valueOf(b.value).equalsIgnoreCase(text)) {
                return b;
            }
        }
        return UNKNOWN; // Връща UNKNOWN вместо да хвърля Exception
    }
}
