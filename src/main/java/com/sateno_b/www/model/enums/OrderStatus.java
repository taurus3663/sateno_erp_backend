package com.sateno_b.www.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum OrderStatus {
    PROCESSING("processing"),
    SENT("sent"),
    CANCELLED("cancelled"),
    ABANDONED("abandoned"),
    COMPLETED("completed"),
    APPROVED("approved"),
    JOINT("joint"),
    WAITING("waiting"),
    FAILED("failed"),
    REFUSED_AFTER_REVIEW("refused_after_review");
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
        return PROCESSING; // Връща UNKNOWN вместо да хвърля Exception
    }
}
