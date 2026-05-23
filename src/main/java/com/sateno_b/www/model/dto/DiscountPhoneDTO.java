package com.sateno_b.www.model.dto;

import com.sateno_b.www.shared.Shared;
import lombok.Data;

@Data
public class DiscountPhoneDTO {

    private Long id;
    private String site;
    private String phone;
    private String created;
    private boolean hasOrder;

    public String getPhone() {
        return Shared.fixBGNumber(phone);
    }
}
