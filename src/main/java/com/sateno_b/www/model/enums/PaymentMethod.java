package com.sateno_b.www.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum PaymentMethod {
    BACS("bacs", "Банков превод"),
    CHEQUE("cheque", "Чек"),
    PAYPAL("paypal", "PayPal"),
    STRIPE("stripe", "Stripe"),
    CARD("card", "Карта"),
    COD("cod", "Наложен платеж"),
    UNKNOWN("unknown", "Неизвестен метод"); // Предпазна мрежа

    private final String code;
    private final String description;

    PaymentMethod(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static PaymentMethod fromString(String value) {
        if (value == null) return UNKNOWN;
        for (PaymentMethod method : PaymentMethod.values()) {
            if (method.code.equalsIgnoreCase(value)) {
                return method;
            }
        }
        return UNKNOWN; // Вместо да гърми, записва "unknown"
    }
}
