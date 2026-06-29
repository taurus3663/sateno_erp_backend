package com.sateno_b.www.shared;

import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.entity.data.PaoIdValue;
import com.sateno_b.www.model.entity.data.PaoIdValueValue;

import java.math.BigDecimal;

public class Shared {

    /**
     * Реален ред-тотал: ако totalPrice > 0 — взима го директно;
     * иначе (платено с карта → totalPrice=0) изчислява price*qty + addon rawPrices.
     */
    public static BigDecimal computeEffectiveLineTotal(OrderLineItem line) {
        BigDecimal tp = line.getTotalPrice();
        if (tp != null && tp.compareTo(BigDecimal.ZERO) > 0) {
            return tp;
        }
        BigDecimal unitPrice = line.getPrice() != null ? line.getPrice() : BigDecimal.ZERO;
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(line.getQuantity()));
        if (line.getPaoIdValue() != null) {
            for (PaoIdValue pao : line.getPaoIdValue()) {
                if (pao.getValue() != null) {
                    for (PaoIdValueValue v : pao.getValue()) {
                        if (v.getRawPrice() != null && !v.getRawPrice().isBlank()) {
                            try {
                                total = total.add(new BigDecimal(v.getRawPrice()));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }
        return total;
    }

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
