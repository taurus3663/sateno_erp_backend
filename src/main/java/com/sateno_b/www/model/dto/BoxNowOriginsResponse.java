package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class BoxNowOriginsResponse {

//{data=[{id=2, type=any-apm, image=null, lat=null, lng=null, region=null, title=, name=Any-APM, addressLine1=Wildcard
// APM used as an origin location, addressLine2=null, postalCode=null, country=null, note=Wildcard APM used as an origin location}]}


    private List<OriginData> data;

    @Data
    public static class OriginData {
        private String id;
        private String type;
        private String name;
        private String title;
        private String addressLine1;
        private String addressLine2;
        private String postalCode;
        private String country;
        private String lat;
        private String lng;
        private String note;
        private String image;
        private String region;
    }
}
