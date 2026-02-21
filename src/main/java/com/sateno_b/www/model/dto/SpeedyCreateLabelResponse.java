package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpeedyCreateLabelResponse {
    // Номер на товарителницата
    private String id;

    // Списък с генерираните пакети
    private List<Parcel> parcels;

    // Дата на взимане
    private String pickupDate;

    // Краен срок за доставка
    private String deliveryDeadline;

    // Ценова информация
    private Price price;

    // Обект за грешки (ако има такива)
    private Error error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Parcel {
        private String id;
        private Integer seqNo;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Price {
        private Double amount;
        private Double vat;
        private Double total;
        private String currency;
        private Map<String, PriceDetail> details;
        private Double amountLocal;
        private Double vatLocal;
        private Double totalLocal;
        private String currencyLocal;
        private Double currencyExchangeRate;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceDetail {
        private Double amount;
        private Double percent;
        private Double vatPercent;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Error {
        private String context;
        private String message;
        private String id;
        private Integer code;
        private String component;
    }
}
