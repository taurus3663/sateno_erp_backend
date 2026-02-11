package com.sateno_b.www.model.interfaces;

public interface ShippingProvider {

    void generateWayBill(Long orderId, Long siteId);
    String getStatus(String wayBillNumber);
}
