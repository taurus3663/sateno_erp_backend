package com.sateno_b.www.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ProductStatus {
    DRAFT("draft"),
    PUBLISHED("publish"),
    PRIVATE("private"); // Добре е да го имаш за всеки случай

    private final String value;

    ProductStatus(String value) {
        this.value = value;
    }

    @JsonValue // Това казва на Jackson: "В JSON-а ще виждаш 'draft' или 'publish'"
    public String getValue() {
        return value;
    }

    @JsonCreator // Това казва на Jackson: "Ето как да превърнеш стринга в Enum"
    public static ProductStatus fromString(String text) {
        for (ProductStatus status : ProductStatus.values()) {
            if (status.value.equalsIgnoreCase(text)) {
                return status;
            }
        }
        return DRAFT;
    }
}
