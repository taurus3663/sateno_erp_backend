package com.sateno_b.www.model.entity.data;

import lombok.Data;

import java.util.List;

@Data
public class CourierContractDetails {

    //    {clients=[{clientId=208633878000, clientName=БАШАРАН ТЕКСТИЛ ЕООД, objectName=SATENO.BG, contactName=ЕВЕЛИНА ЯНКОВА,
//    address={countryId=100, siteId=56784, siteType=гр., siteName=ПЛОВДИВ, postCode=4000, streetId=11011, streetType=ул.,
//    streetName=СРЕДЕЦ, streetNo=40, x=24.739786, y=42.156513, fullAddressString=гр. ПЛОВДИВ ул. СРЕДЕЦ No 40,
//    siteAddressString=гр. ПЛОВДИВ, localAddressString=ул. СРЕДЕЦ No 40}, email=vippbradar@gmail.com, privatePerson=false,
//    phones=[{number=0889020222}, {number=0892910094}]}]}
    private Long clientId;
    private String clientName;
    private String objectName;
    private String contactName;
    private AddressDetails address;
    private String email;
    private Boolean privatePerson;
    private List<PhoneDetails> phones;

    @Data
    public static class AddressDetails {
        private Long siteId;
        private String siteName;
        private String fullAddressString;
        private String postCode;
        // добави и другите полета, ако ти трябват
    }

    @Data
    public static class PhoneDetails {
        private String number;
    }
}
