package com.sateno_b.www.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.WoOrderDto;
import com.sateno_b.www.model.dto.WoPaoIdValueValueDto;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.entity.data.PaoIdValue;
import com.sateno_b.www.model.entity.data.PaoIdValueValue;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpOrderRepository;
import com.sateno_b.www.shared.AuthTool;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WpOrderService {

    private final WpOrderRepository wpOrderRepository;
    private final RestClient restClient;
    private final SiteRepository siteRepository;
    private final ObjectMapper objectMapper;

    private static final String ORDER_URL = "/wp-json/wc/v3/orders/";

    @Transactional
    public void syncOrderToDB(Long siteId){
        SiteEntity site = siteRepository.findById(siteId).orElse(null);
        if(site == null) {return;}

        String auth = AuthTool.getAuth(site.getConsumerKey(), site.getConsumerSecret());

        List<WoOrderDto> all = fetchAllOrders(site, auth);

        for (WoOrderDto dto : all) {
            WpOrderEntity wpOrderEntity = new WpOrderEntity();
            wpOrderEntity.setWpOrderId(dto.getId());
            wpOrderEntity.setOrderLine(dto.getLineItems()
                    .stream()
                    .map(woOrderLineItemDto -> {
                        OrderLineItem orderLineItem = new OrderLineItem();
                        orderLineItem.setSku(woOrderLineItemDto.getSku());
                        orderLineItem.setQuantity(woOrderLineItemDto.getQuantity());
                        orderLineItem.setPrice(woOrderLineItemDto.getSubtotal());
                        orderLineItem.setProductId(woOrderLineItemDto.getProductId());
                        orderLineItem.setProductName(woOrderLineItemDto.getProductName());
                        orderLineItem.setTotalPrice(woOrderLineItemDto.getTotal());
                        orderLineItem.setPaoIdValue(woOrderLineItemDto.getPaoIdValue()
                                .stream()
                                .filter(node -> "_pao_ids".equals(node.getKey()))
                                .map(woPaoIdValueDto -> {
                                    PaoIdValue paoIdValue = new PaoIdValue();
                                    paoIdValue.setId(woPaoIdValueDto.getId());
                                    paoIdValue.setKey(woPaoIdValueDto.getKey());

                                    List<WoPaoIdValueValueDto> valueDtos = objectMapper.convertValue(
                                            woPaoIdValueDto.getValue(),
                                            new TypeReference<List<WoPaoIdValueValueDto>>() {}
                                    );

                                    paoIdValue.setValue(valueDtos.stream().map(vDto -> {
                                        PaoIdValueValue val = new PaoIdValueValue();
                                        val.setId(vDto.getId());
                                        val.setKey(vDto.getKey());
                                        val.setRawValue(vDto.getRawValue());
                                        val.setRawPrice(vDto.getRawPrice());
                                        val.setValue(vDto.getValue());
                                        val.setPriceType(vDto.getPriceType());
                                        return val;
                                    }).toList());

//                                    paoIdValue.setValue(woPaoIdValueDto.getValue()
//                                            .stream()
//                                            .map(woPaoIdValueValueDto -> {
//                                                PaoIdValueValue  paoIdValueValue = new PaoIdValueValue();
//                                                paoIdValueValue.setId(woPaoIdValueValueDto.getId());
//                                                paoIdValueValue.setKey(woPaoIdValueValueDto.getKey());
//                                                paoIdValueValue.setRawValue(woPaoIdValueValueDto.getRawValue());
//                                                paoIdValueValue.setRawPrice(woPaoIdValueValueDto.getRawPrice());
//                                                paoIdValueValue.setValue(woPaoIdValueValueDto.getValue());
//                                                return paoIdValueValue;
//                                            }).toList());
                                    return paoIdValue;
                                }).toList());
                        orderLineItem.setProductName(woOrderLineItemDto.getProductName());
                        return orderLineItem;
                    }).toList());
            wpOrderEntity.setBilling(dto.getBilling());
            wpOrderEntity.setShipping(dto.getShipping());
            wpOrderEntity.setCurrency(dto.getCurrency());
            wpOrderEntity.setCurrency_symbol(dto.getCurrencySymbol());
            wpOrderEntity.setStatus(dto.getStatus());
            wpOrderEntity.setCustomerIp(dto.getCustomerIpAddress());
            wpOrderEntity.setCustomerAgent(dto.getCustomerUserAgent());
            wpOrderEntity.setTotalPrice(new BigDecimal(dto.getTotal()));
            wpOrderEntity.setPaymentMethod(dto.getPaymentMethod());
            wpOrderEntity.setTransactionId(dto.getTransactionId());
            wpOrderRepository.save(wpOrderEntity);
        }
    }

    private List<WoOrderDto> fetchAllOrders(SiteEntity site, String auth){
        List<WoOrderDto> allOrders = new ArrayList<>();

        int currentPage = 1;
        int totalPages = 1;

        do {

            var response = restClient.get()
                    .uri(site.getUrl() + ORDER_URL + "?per_page=1&page=" + currentPage + "&orderby=id&order=desc")
                    .header("Authorization", "Basic " + auth)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<WoOrderDto>>() {});


            if(response.getBody() != null) {
                allOrders.addAll(response.getBody());
            }


            currentPage++;
        } while (currentPage <= totalPages);

        return allOrders;
    }
}
