package com.sateno_b.www.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EcontCreateLabelRequest {
    private EcontLabel label;
    private String mode = "create";
    private String runAsyncAndEmailResultTo = "";

    @Data
    public static class EcontLabel {
        private Client senderClient;
        private Address senderAddress;
        private String senderOfficeCode;

        private Client receiverClient;
        private Address receiverAddress;
        private String receiverOfficeCode;

        private String packCount;
        private String shipmentType = "PACK";
        private String weight;
        private String sizeUnder60cm = "1";
        private String shipmentDescription;
        private Services services;
    }

    @Data
    @AllArgsConstructor
    public static class Client {
        private String name;
        private List<String> phones;
    }

    @Data
    public static class Address {
        private City city;
        private String street;
        private String num;
        private String other;
    }

    @Data
    @AllArgsConstructor
    public static class City {
        private String name;
        private String postCode;
        private Country country = new Country();

        public City(String name, String postCode) {
            this.name = name;
            this.postCode = postCode;
        }
    }

    @Data
    public static class Country {
        private String code3 = "BGR";
    }

    @Data
    @AllArgsConstructor
    public static class Services {
        private Double cdAmount;
        private String cdCurrency = "EUR";
    }
}
