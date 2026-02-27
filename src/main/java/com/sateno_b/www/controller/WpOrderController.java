package com.sateno_b.www.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.CustomerEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.enums.PaymentMethod;
import com.sateno_b.www.model.enums.WsAction;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.model.repository.WpOrderRepository;
import com.sateno_b.www.service.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
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
    private final EcontService econtService;
    private final SpeedyService speedyService;
    private final CourierSettingsRepository courierSettingsRepository;
    private final BoxNowService boxNowService;

    @GetMapping("/list")
    public ResponseEntity<Page<WpOrderDto>> getAll(Pageable pageable, @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String phone,
                                                   @RequestParam(required = false) String customer) {

        Pageable sortedByIdDesc = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("wpOrderTime").descending() // Първо по най-нова дата
                        .and(Sort.by("id").descending()) // После по ID, ако датите са еднакви
        );


        OrderStatus orderStatus = (status != null) ? OrderStatus.fromValue(status) : null;

        Page<WpOrderEntity> wpOrderEntities = wpOrderRepository.findWithFilters(orderStatus, phone, customer, sortedByIdDesc);

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
//                        System.out.printf("Duplicates found: %s\n", allDuplicateLines.size());
//                        System.out.println(dto.getWpOrderId());
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
//            notificationService.sendUpdate("orders", WsAction.UPDATED);
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
//                    System.out.println(dto.toString());
                    // 3. Извикваме сървиса за запис в базата
                    wpOrderService.newOrderFromSite(dto, siteId);

//                    log.info("Успешно обработена поръчка #{} от сайт ID: {}", dto.getId(), siteId);

//                    notificationService.sendUpdate("orders", WsAction.UPDATED);
                } catch (JsonProcessingException e) {
                    log.error("Грешка при парсване на JSON от WooCommerce: {}", e.getMessage());

                } catch (Exception e) {
                    log.error("Грешка при обработка на поръчката: {}", e.getMessage());

                }
            }

    }

    @PostMapping("/create/waybill")
    public ResponseEntity<?> createWayBill(@RequestBody CreateLabelDto createLabelDto) {

        Optional<WpOrderEntity> byId = wpOrderRepository.findById(createLabelDto.getId());

       if(byId.isPresent()){
           // Проверка
           // дали поръчката вече има генерирана товарителница
           if (byId.get().getWayBillShipmentNumber() != null || byId.get().getCourierId() != null) {
               return ResponseEntity
                       .badRequest()
                       .body("Поръчката вече има генерирана товарителница!");
           }
       } else {
           return ResponseEntity
                   .badRequest()
                   .body("ИД не съществува");
       }

//        System.out.println(createLabelDto.toString());
        Object rs = new Object();
    try {
        if(createLabelDto.getCourierType() == CourierType.ECONT){
            rs = econtService.createWayBill(createLabelDto);
        } else if(createLabelDto.getCourierType() == CourierType.SPEEDY) {
            rs = speedyService.createWayBill(createLabelDto);
        } else if(createLabelDto.getCourierType() == CourierType.BOX_NOW) {
            rs = boxNowService.createWayBill(createLabelDto);
        }
        return ResponseEntity.ok(rs);
    } catch (Exception e) {
        log.error(e.getMessage());
        e.printStackTrace();
        return ResponseEntity.internalServerError().body(e.getMessage());
    }
    }

    private static final Pattern REGEX_1 = Pattern.compile("^\\[(OFFICE|LOCKER|ADDRESS)\\]\\s*(.*)\\s*\\[(.*?)\\]\\s*\\[(SPEEDY|ECONT|BOXNOW)\\]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_2 = Pattern.compile("До\\s+(офис|адрес|автомат)\\s+(speedy|econt|boxnow)\\[(.*?)\\]:\\s*(.*)", Pattern.CASE_INSENSITIVE);

    @PostMapping("generate/waybill/{orderId}/{waybillIds}/{paperSize}")
    public ResponseEntity<byte[]> generateWayBill(@PathVariable("orderId") Long orderId, @PathVariable("waybillIds") List<String> waybillIds, @PathVariable String paperSize){
        byte[] pdfBytes = new byte[0];
        
        Optional<WpOrderEntity> byId = wpOrderRepository.findById(orderId);

        if(byId.isPresent()){
            WpOrderEntity order = byId.get();
//            String shippingMethod = order.getBilling().getAddress1();
//            String courierName = "";
//            String deliveryType = "";
//            Matcher m1 = REGEX_1.matcher(shippingMethod);
//            if (m1.find()) {
//                deliveryType = m1.group(1);
//                courierName = m1.group(4);
//            } else {
//                // Опит за парсване с Regex 2
//                Matcher m2 = REGEX_2.matcher(shippingMethod);
//                if (m2.find()) {
//                    courierName = m2.group(2);
//                    deliveryType = m2.group(1);
//                }
//            }

            Optional<CourierSettingsEntity> courierSettings = courierSettingsRepository.findById(order.getCourierId());
            if(courierSettings.isPresent()){
                        CourierSettingsEntity courierSetting = courierSettings.get();
                if(order.getCourierType() == CourierType.SPEEDY) {
                    pdfBytes = speedyService.getWaybillPdf(waybillIds, paperSize, courierSetting.getUsername(), courierSetting.getPassword());
                } else if(order.getCourierType() == CourierType.BOX_NOW) {
                    pdfBytes = boxNowService.getWaybillPdf(waybillIds, paperSize, courierSetting.getApiKey(), courierSetting.getApiSecret(), order);
                }
            } else {
                return ResponseEntity.notFound().build();
            }

            if (pdfBytes == null || pdfBytes.length == 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"label-" + waybillIds + ".pdf\"")
                .body(pdfBytes);
    }

    @PostMapping("/cancel-shipment/{orderId}")
    public ResponseEntity<?> cancelShipment(@PathVariable Long orderId){
        boolean result = false;
        try {
            WpOrderEntity order = wpOrderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Поръчката не е намерена "));

            if(order.getWayBillShipmentNumber() == null){
                throw new RuntimeException("Поръчката няма генерирана товарителница");
            }

            Optional<CourierSettingsEntity> courierSetting = courierSettingsRepository.findById(order.getCourierId());

            if(courierSetting.isPresent()){
                CourierSettingsEntity courier =  courierSetting.get();
                if(courier.getCourierType() == CourierType.SPEEDY){
                    result = speedyService.cancelShipment(order, courier);
                } else if(courier.getCourierType() == CourierType.BOX_NOW){
                    result = boxNowService.cancelShipment(order, courier);
                } else if (courier.getCourierType() == CourierType.ECONT) {
                    result = econtService.cancelShipment(order, courier);
                }


            }


            return ResponseEntity.ok(Map.of("success", result));

        } catch (Exception e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
