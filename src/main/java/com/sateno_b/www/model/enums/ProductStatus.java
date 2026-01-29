package com.sateno_b.www.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ProductStatus {
    DRAFT("draft"),      // Индекс 0
    PUBLISHED("publish"), // Индекс 1
    PRIVATE("private");   // Индекс 2

    private final String value;

    ProductStatus(String value) {
        this.value = value;
    }

    @JsonValue // Слагаме го тук, за да връща 0, 1 или 2 в JSON
    public int getOrdinal() {
        return this.ordinal();
    }

    @JsonCreator // Jackson ще получи число и ще го превърне в Enum
    public static ProductStatus fromInt(int index) {
        for (ProductStatus status : ProductStatus.values()) {
            if (status.ordinal() == index) {
                return status;
            }
        }
        return DRAFT; // Default стойност
    }
}
