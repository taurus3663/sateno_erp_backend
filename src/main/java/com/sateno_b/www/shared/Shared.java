package com.sateno_b.www.shared;

public class Shared {

    public static String fixBGNumber(String number) {
        String rawPhone = number.replaceAll("[^0-9]", "");

        if (rawPhone.startsWith("00359")) {
            rawPhone = "0" + rawPhone.substring(5); // Реже първите 5 цифри и слага 0
        } else if (rawPhone.startsWith("359")) {
            rawPhone = "0" + rawPhone.substring(3); // Реже първите 3 цифри и слага 0
        }

        return rawPhone;
    }
}
