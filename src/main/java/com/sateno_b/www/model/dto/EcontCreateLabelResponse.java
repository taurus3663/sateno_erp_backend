package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EcontCreateLabelResponse {
    private List<EcontResult> results;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EcontResult {
        private EcontLabelData label;
        private String error;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EcontLabelData {
        private String shipmentNumber;
        private String shipmentType;
        private Integer packCount;
        private Double weight;
        private String shipmentDescription;

        // Ценови компоненти
        private Double totalPrice;
        private String currency;
        private Double senderDueAmount;
        private Double receiverDueAmount;

        // Линк за печат
        private String pdfURL;

        // Дати
        private Long createdTime;
        private Long expectedDeliveryDate;

        // Списък с услуги (за детайлна разбивка на цената)
        private List<EcontServiceResponse> services;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EcontServiceResponse {
        private String type;
        private String description;
        private Integer count;
        private String paymentSide;
        private Double price;
        private String currency;
    }
}
