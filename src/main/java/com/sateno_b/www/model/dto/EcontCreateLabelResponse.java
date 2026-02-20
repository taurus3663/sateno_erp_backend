package com.sateno_b.www.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EcontCreateLabelResponse {
    // В единичната заявка "label" е на най-горно ниво
    private EcontLabelData label;

    // Понякога Еконт връща тези на горно ниво при грешка или предупреждение
    private String courierRequestID;
    private String delayedDeliveryWarning;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EcontLabelData {
        private String shipmentNumber;
        private String shipmentType;
        private Integer packCount;
        private Double weight;
        private String shipmentDescription;
        private Double totalPrice;
        private String currency;
        private Double receiverDueAmount;
        private String pdfURL;
        private Long createdTime;
        private Long expectedDeliveryDate;
        private List<EcontServiceResponse> services;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EcontServiceResponse {
        private String type;
        private String description;
        private Double price;
        private String currency;
        private String paymentSide;
    }
}
