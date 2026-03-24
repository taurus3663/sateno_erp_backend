package com.sateno_b.www.shared;

import com.sateno_b.www.model.entity.WpOrderEntity;
import lombok.Getter;
import lombok.ToString;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CourierParser {

    @Getter
    @ToString
    public static class CourierMatch {
        private final String courier;    // ECONT, SPEEDY, BOX_NOW
        private final String targetType; // OFFICE, LOCKER, ADDRESS
        private final String code;       // Код на офис/автомат
        private final String address;    // Чистият адрес
        private final String city;
        private final String postcode;

        public CourierMatch(String courier, String targetType, String code, String address, String city, String postcode) {
            this.city = city;
            this.postcode = postcode;
            // Стандартизираме името на куриера (BOXNOW -> BOX_NOW)
            String c = courier.split("-")[0].toUpperCase();
            this.courier = c.equals("BOXNOW") ? "BOX_NOW" : c;

            this.targetType = mapTargetType(targetType);
            this.code = code != null ? code.trim() : "";
            this.address = address != null ? address.trim() : "";
        }

        private String mapTargetType(String raw) {
            String t = raw.toLowerCase();
            if (t.contains("office") || t.contains("офис")) return "OFFICE";
            if (t.contains("locker") || t.contains("автомат")) return "LOCKER";
            if (t.contains("address") || t.contains("адрес")) return "ADDRESS";
            return "UNKNOWN";
        }

        public String getEcontReceiverType() {
            return "ADDRESS".equals(targetType) ? "door" : "office";
        }
    }

    private static final Pattern REGEX1 = Pattern.compile("^\\[(OFFICE|LOCKER|ADDRESS)\\]\\s*(.*)\\s*\\[(.*?)\\]\\s*\\[(SPEEDY|ECONT|BOXNOW)\\]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX2 = Pattern.compile("До\\s+(офис|адрес|автомат)\\s+(speedy|econt|boxnow)(?:-[a-z]+)?\\s*\\[(.*?)\\]:\\s*(.*)", Pattern.CASE_INSENSITIVE);

    public static CourierMatch parse(String address) {
        if (address == null || address.isBlank()) return null;

        String cleanAddress = address.trim();

        Matcher m1 = REGEX1.matcher(cleanAddress);
        if (m1.find()) {
            // m1.group(2) в Regex1 обикновено е името на офиса/града
            return new CourierMatch(m1.group(4), m1.group(1), m1.group(3), m1.group(2), "", "");
        }

        Matcher m2 = REGEX2.matcher(cleanAddress);
        if (m2.find()) {
            // m2.group(4) в Regex2 е адресната част след двоеточието
            return new CourierMatch(m2.group(2), m2.group(1), m2.group(3), m2.group(4), "", "");
        }

        return null;
    }

    public static CourierMatch parseWithFallback(WpOrderEntity order) {
        if (order == null || order.getBilling() == null) return null;

        String addr = order.getBilling().getAddress1();
        String city = order.getBilling().getCity();
        String postcode = order.getBilling().getPostalCode();

        // 1. Първо пробваме през Regex (за поръчки със специален формат)
        CourierMatch match = parse(addr);
        if (match != null) return match;
        // 2. FALLBACK: Търсим в заглавието на метода за доставка (shipping_lines)
        if (order.getShippingLines() != null && !order.getShippingLines().isEmpty()) {
            var shippingLine = order.getShippingLines().get(0);
            String title = shippingLine.getMethodTitle().toLowerCase();

            String courier = null;
            String mode = "ADDRESS";

            if (title.contains("econt") || title.contains("еконт")) courier = "ECONT";
            else if (title.contains("speedy") || title.contains("спиди")) courier = "SPEEDY";
            else if (title.contains("boxnow") || title.contains("box-now")) {
                courier = "BOX_NOW";
                mode = "LOCKER";
            }

            if (courier != null) {
                if (title.contains("офис") || title.contains("office")) mode = "OFFICE";
                else if (title.contains("автомат") || title.contains("locker") || title.contains("aps")) mode = "LOCKER";

                return new CourierMatch(courier, mode, "", addr, city, postcode);
//                System.out.printf("COURIER: %s, MODE: %s, ADDRESS: %s%n", courier, mode, addr);
            }
        }
        return null;
    }


}