package com.sateno_b.www.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.OrderLineItemDto;
import com.sateno_b.www.model.dto.WoOrderDto;
import com.sateno_b.www.model.dto.WoOrderLineItemDto;
import com.sateno_b.www.model.dto.WpOrderDto;
import com.sateno_b.www.model.entity.CustomerEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.enums.PaymentMethod;
import com.sateno_b.www.model.enums.WsAction;
import com.sateno_b.www.model.repository.WpOrderRepository;
import com.sateno_b.www.service.NotificationService;
import com.sateno_b.www.service.WebHookService;
import com.sateno_b.www.service.WpOrderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/wp_order")
@RequiredArgsConstructor
@Slf4j
public class WpOrderController {

    private final WpOrderRepository wpOrderRepository;
    private final ModelMapper modelMapper;
    private final WpOrderService wpOrderService;
    private final WebHookService webHookService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @GetMapping("/list")
    public ResponseEntity<Page<WpOrderDto>> getAll(Pageable pageable, @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String phone) {

        Pageable sortedByIdDesc = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("wpOrderTime").descending() // Първо по най-нова дата
                        .and(Sort.by("id").descending()) // После по ID, ако датите са еднакви
        );


        OrderStatus orderStatus = (status != null) ? OrderStatus.fromValue(status) : null;

        Page<WpOrderEntity> wpOrderEntities = wpOrderRepository.findWithFilters(orderStatus, phone, sortedByIdDesc);

        List<CustomerEntity> customers = wpOrderEntities.getContent().stream()
                .map(WpOrderEntity::getCustomer)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Long> customerCounts = new HashMap<>();
        if (!customers.isEmpty()) {
            List<Object[]> results = wpOrderRepository.countByCustomersBatch(customers);
            customerCounts = results.stream().collect(Collectors.toMap(
                    res -> (Long) res[0], // ID на клиента
                    res -> (Long) res[1]  // Брой поръчки
            ));
        }
        Map<Long, Long> finalCounts = customerCounts;

        Page<WpOrderDto> wpOrderDtos =wpOrderEntities.map(entity -> {
            WpOrderDto dto = modelMapper.map(entity, WpOrderDto.class);

            if(entity.getStatus() == OrderStatus.PROCESSING && entity.getCustomer() != null) {

                String phone1 = entity.getCustomer().getPhone();
                if(phone1 != null && !phone1.isEmpty()) {
                    List<WpOrderEntity> duplicates = wpOrderRepository.findDuplicatesWithLines(
                            phone1, OrderStatus.PROCESSING, entity.getId()
                    );

                    List<OrderLineItemDto> allDuplicateLines = duplicates.stream()
                            .flatMap(dup -> dup.getOrderLine().stream().map(line -> {
                                // Мапваме продукта
                                OrderLineItemDto lineDto = modelMapper.map(line, OrderLineItemDto.class);
                                // Заковаваме ID-то на поръчката, от която идва
                                lineDto.setOrderId(dup.getId());
                                lineDto.setWpOrderId(dup.getWpOrderId());
                                return lineDto;
                            }))
                            .collect(Collectors.toList());
                    if(!allDuplicateLines.isEmpty()) {
                        System.out.printf("Duplicates found: %s\n", allDuplicateLines.size());
                        System.out.println(dto.getWpOrderId());
                        dto.setShowDuplicateWarning(true);
                    }

                    dto.setOrderLineOtherOrders(allDuplicateLines);
                }

            }

            if(entity.getCustomer() != null) {
                long count = finalCounts.getOrDefault(entity.getCustomer().getId(), 0L);
                dto.setCustomerOrderCount(count);
            }
//            long count = wpOrderRepository.countByCustomer(entity.getCustomer());
//            dto.setCustomerOrderCount(count);
//            System.out.println(count);
            return dto;
        });

        return ResponseEntity.ok(wpOrderDtos);
    }

    @PostMapping("/sync/{siteId}")
    public void syncWpOrder(@PathVariable Long siteId){
            wpOrderService.syncOrderToDB(siteId);
    }

    @PostMapping("/save")
    @Transactional
    public ResponseEntity<WpOrderDto> saveWpOrder(@RequestBody WpOrderDto wpOrderDto){
        Optional<WpOrderEntity> byId = wpOrderRepository.findById(wpOrderDto.getId());
        if(byId.isPresent()){
            byId.get().setStatus(wpOrderDto.getStatus());

            if(wpOrderDto.getOrdersToMerge() != null && !wpOrderDto.getOrdersToMerge().isEmpty()) {
                
                List<WpOrderEntity> ordersToMerge = wpOrderRepository.findAllById(wpOrderDto.getOrdersToMerge());

                for (WpOrderEntity wpOrderEntity : ordersToMerge) {

                    boolean isPayed = wpOrderEntity.getPaymentMethod() == PaymentMethod.CARD ||
                            wpOrderEntity.getPaymentMethod() == PaymentMethod.STRIPE ||
                            wpOrderEntity.getPaymentMethod() == PaymentMethod.STRIPE_APPLEPAY ||
                            wpOrderEntity.getPaymentMethod() == PaymentMethod.STRIPE_CC;

                    for (OrderLineItem orderLineItem : wpOrderEntity.getOrderLine()) {

                        OrderLineItem newItem = new OrderLineItem();
                        newItem.setProductName(orderLineItem.getProductName());
                        newItem.setQuantity(orderLineItem.getQuantity());
                        newItem.setSku(orderLineItem.getSku());
                        newItem.setImage(orderLineItem.getImage());
                        newItem.setPaoIdValue(orderLineItem.getPaoIdValue());

                        if(isPayed){
                            newItem.setPrice(BigDecimal.valueOf(0));
                            newItem.setTotalPrice(BigDecimal.valueOf(0));
                        } else {
                            newItem.setPrice(orderLineItem.getPrice());
                            newItem.setTotalPrice(orderLineItem.getTotalPrice());

                            byId.get().setTotalPrice(byId.get().getTotalPrice().add(newItem.getTotalPrice()));
                        }
                        wpOrderEntity.setStatus(OrderStatus.JOINT);
                        wpOrderEntity.setParentId(byId.get().getId());
                        byId.get().getOrderLine().add(newItem);
                        wpOrderRepository.save(wpOrderEntity);
                    }
                }
                
            }

            wpOrderRepository.save(byId.get());
            notificationService.sendUpdate("orders", WsAction.UPDATED);
            return ResponseEntity.ok(wpOrderDto);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/create")
    public void createWpOrder(@RequestHeader("x-wc-webhook-signature") String signature,
                              @RequestBody String rawPayload){

            Long siteId = webHookService.validateAndGetSiteId(rawPayload, signature);
            if(siteId != null){

                try {
                    // 2. Ръчно десериализираме JSON-а към DTO
//                    ObjectMapper objectMapper = new ObjectMapper();
                    // Използваме ObjectMapper, защото rawPayload ни трябваше като String за проверката
                    WoOrderDto dto = objectMapper.readValue(rawPayload, WoOrderDto.class);

                    // 3. Извикваме сървиса за запис в базата
                    wpOrderService.newOrderFromSite(dto, siteId);

//                    log.info("Успешно обработена поръчка #{} от сайт ID: {}", dto.getId(), siteId);

                    notificationService.sendUpdate("orders", WsAction.UPDATED);
                } catch (JsonProcessingException e) {
                    log.error("Грешка при парсване на JSON от WooCommerce: {}", e.getMessage());

                } catch (Exception e) {
                    log.error("Грешка при обработка на поръчката: {}", e.getMessage());

                }
            }

    }
}
