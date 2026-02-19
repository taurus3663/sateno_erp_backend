package com.sateno_b.www.model.entity.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ShippingLines {
    private Long id;
    @JsonProperty("method_title")
    private String methodTitle;

    @JsonProperty("method_id")
    private String methodId;

    @JsonProperty("instance_id")
    private String instanceId;

    private String total;
    @JsonProperty("total_tax")
    private String totalTax;

    @JsonProperty("tax_status")
    private String taxStatus;
}
