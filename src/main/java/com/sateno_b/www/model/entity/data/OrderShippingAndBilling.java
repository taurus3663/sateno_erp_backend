package com.sateno_b.www.model.entity.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OrderShippingAndBilling {

    @JsonProperty("first_name")
    private String firstName;
    @JsonProperty("last_name")
    private String lastName;
    @JsonProperty("company")
    private String companyName;
    @JsonProperty("address_1")
    private String address1;
    @JsonProperty("address_2")
    private String address2;
    private String city;
    private String state;
    @JsonProperty("postcode")
    private String postalCode;
    private String country;
    private String email;
    private String phone;
}
