package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class BoxNowDeliveryResponse {
    private String id; // Това е ID на самата заявка (77208863)
    private List<Parcel> parcels;

    @Data
    public static class Parcel {
        private String id; // Това е номерът на товарителницата (3387156697)
    }

}
