package com.sateno_b.www.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Входящо събитие от tracker-а на сайта (браузър beacon или PHP hook).
 * type: visitor | product_view | category_view | cart_update | checkout_start | checkout_data | order_complete | leave
 */
@Data
public class LiveEventDto {

    private String type;
    private String site;        // домейн на сайта (напр. "sateno.bg")
    private String session;     // анонимен токен на сесията

    private String page;        // текуща страница (по избор)

    // продукт (за product_view)
    private Long productId;
    private String sku;
    private String productName;
    private String productImage;

    // категория (за category_view)
    private Long categoryId;
    private String categoryName;

    // източник на трафик (tracker-ът ги праща при първото събитие в сесията)
    private String referrer;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;

    // стъпка във фунията на касата: data | shipping | payment (по избор)
    private String checkoutStep;

    // попълват се от СЪРВЪРА (LiveController) от HTTP хедърите — НЕ идват от тялото
    private String userAgent;   // за парсване на устройство/браузър
    private String clientIp;    // за по-късна geo резолюция (не се излага към Claude)

    // количка / каса
    private BigDecimal cartValue;
    private String currency;
    private List<Item> items;
    private Boolean onCart;      // true → събитието идва от страницата на количката (за статус „Разглежда количката")
    private Boolean onCheckout;  // true → клиентът е на страницата на касата

    // данни на клиента (за checkout_data)
    private String name;
    private String phone;
    private String email;
    private Boolean hasData;

    // завършена поръчка
    private String orderId;

    @Data
    public static class Item {
        private Long productId;
        private String sku;
        private String name;
        private String image;
        private Integer qty;
        private BigDecimal price;
    }
}
