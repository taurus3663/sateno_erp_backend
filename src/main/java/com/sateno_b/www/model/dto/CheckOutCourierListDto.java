package com.sateno_b.www.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CheckOutCourierListDto {

    private List<CheckOutCourierDto> checkOutCourierList = new ArrayList<>();
    private String currencySymbol;
    private String currencyName;
//    private

}
