package com.sateno_b.www.service;

import com.sateno_b.www.model.interfaces.ShippingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class SpeedyService implements ShippingProvider {


    @Override
    public void generateWayBill(Long orderId, Long siteId) {

    }

    @Override
    public String getStatus(String wayBillNumber) {
        return "";
    }
}
