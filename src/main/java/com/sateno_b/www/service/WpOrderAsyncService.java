package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.entity.data.OrderShippingAndBilling;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpOrderRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class WpOrderAsyncService {

    private final SiteRepository  siteRepository;
    private final RestClient restClient;
    private final WpOrderRepository wpOrderRepository;

    @Transactional
    @Async
    public void updateOrderOnSites(WpOrderEntity order, Long sourceSiteId) {

//        order = wpOrderRepository.findById(order.getId()).orElse(null);
        List<SiteEntity> siteList = new ArrayList<>();
        if(sourceSiteId != null){
            Optional<SiteEntity> byId = siteRepository.findById(sourceSiteId);
            byId.ifPresent(siteList::add);
        } else {
//            siteList.addAll(siteRepository.findAll());
            Optional<SiteEntity> byId = siteRepository.findById(order.getSite().getId());
            byId.ifPresent(siteList::add);
        }



        for (SiteEntity site : siteList) {
//            if(site.getId().equals(sourceSiteId) || site.getUrl().equals("sateno.bg")) continue;

            try {
            String auth = Base64.getEncoder().encodeToString((site.getConsumerKey() + ":" + site.getConsumerSecret()).getBytes());

            var searchResponse = restClient.get()
                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/orders/" + order.getWpOrderId())
                    .header("Authorization", "Basic " + auth)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (searchResponse == null || searchResponse.isEmpty()) {
                log.warn("Продукт с SKU {} не е намерен в сайта {}", order.getWpOrderId(), site.getUrl());
                continue;
            }

            OrderShippingAndBilling billing = order.getBilling();

            Map<String, Object> billingBody = new HashMap<>();
            billingBody.put("first_name", billing.getFirstName());
            billingBody.put("last_name", billing.getLastName());
            billingBody.put("email", billing.getEmail());
            billingBody.put("phone", billing.getPhone());


            Map<String, Object> updateBody = new HashMap<>();
            updateBody.put("billing", billingBody);
            updateBody.put("shipping", billingBody);
            updateBody.put("payment_method",  order.getPaymentMethod().name());


            String status = "";
            if(order.getStatus() == OrderStatus.APPROVED ||
                    order.getStatus() == OrderStatus.WAITING) {
                status = OrderStatus.PROCESSING.name().toLowerCase();
            }
            else if(order.getStatus() == OrderStatus.JOINT) {
                status = OrderStatus.COMPLETED.name().toLowerCase();
            }

            if(status.isEmpty()) {
                status = order.getStatus().name().toLowerCase();
            }

            updateBody.put("status", status);


            restClient.patch()
                    .uri(site.getUrlWithHttps() + "/wp-json/wc/v3/orders/" + order.getWpOrderId())
                    .header("Authorization", "Basic " + auth)
                    .body(updateBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("УСПЕШНО ОБНОВЕНА ПОРЪЧКА {}", order.getWpOrderId());
        } catch (Exception e) {
                log.error("Грешка при обновяване на сайт {}: {}", site.getUrl(), e.getMessage());
            }
        }

    }
}
