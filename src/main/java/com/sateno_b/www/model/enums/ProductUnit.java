package com.sateno_b.www.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ProductUnit {
    PCS, // quantity
    KG,
    L, // litre
    M; // meters

    @JsonValue
    public int toValue() {
        return ordinal(); // Jackson ще праща 0, 1, 2 към Angular
    }
}
