package com.sateno_b.www.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Снапшот на живото състояние, който се праща към ERP таблото
 * (през WebSocket /topic/live и през GET /erp/live/snapshot).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveSnapshotDto {

    private int visitors;       // активни в момента
    private int visitorsToday;  // уникални посетители днес
    private int ordersToday;    // поръчки днес (за conversion rate)
    private List<CartView> carts;
    private List<CheckoutView> checkouts;
    private List<AbandonedView> abandonedToday;
    private List<ActivityView> activity;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartView {
        private int id;
        private BigDecimal value;
        private String currency;
        private int productsCount;
        private String lastActivity;
        private List<String> images; // URL-и на снимките на продуктите в количката
        private String status;       // „Разглежда количката" — само докато клиентът е на страницата на количката
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckoutView {
        private int id;
        private BigDecimal value;
        private String currency;
        private int productsCount;
        private String status;
        private String lastActivity;
        private List<String> images; // URL-и на снимките на продуктите на касата
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AbandonedView {
        private Long id;
        private Long siteId;
        private String name;
        private String email;
        private String phone;
        private BigDecimal value;
        private String currency;
        private String leftAt;
        private List<AbandonedItem> items;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class AbandonedItem {
            private Long productId;
            private String sku;
            private String name;
            private String image;
            private Integer qty;
            private Double price;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityView {
        private String type;   // visitor | cart | checkout | abandon | order
        private String title;
        private String sub;
        private String time;
    }
}
