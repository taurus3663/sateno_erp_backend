package com.sateno_b.www.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ProductSaleType {
    LIMITED,
    UNLIMITED;

    @JsonValue // Слагаме го тук, за да връща 0, 1 или 2 в JSON
    public int getOrdinal() {
        return this.ordinal();
    }
}
