package com.sateno_b.www.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CourierType {
    SPEEDY,
    ECONT,
    BOX_NOW;


    @JsonCreator
    public static CourierType fromString(String value) {
        if (value == null) return null;
        String clean = value.replace("_", "").replace(" ", "").toUpperCase();

        switch (clean) {
            case "SPEEDY": return SPEEDY;
            case "ECONT": return ECONT;
            case "BOXNOW": return BOX_NOW;
            default: throw new IllegalArgumentException("Unknown courier: " + value);
        }
    }
}
