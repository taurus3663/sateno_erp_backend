package com.sateno_b.www.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.entity.data.OrderShippingAndBilling;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.service.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

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
    private final UserSignalRepository userSignalRepository;
    private final SiteRepository siteRepository;
    private final WpOrderAsyncService wpOrderAsyncService;
    private final NekorektenService nekorektenService;

    @GetMapping("/list")
    public ResponseEntity<Page<WpOrderDto>> getAll(Pageable pageable, @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String phone,
                                                   @RequestParam(required = false) String customer,
                                                   @RequestParam(required = false) Long id) {

        Page<WpOrderDto> all = wpOrderService.getAll(pageable, status, phone, customer, id);
        return ResponseEntity.ok(all);
    }

   @GetMapping("/detail/{id}")
   public ResponseEntity<WpOrderDto> getOne(@PathVariable Long id) {
//       System.out.println();
        WpOrderDto id1 = wpOrderService.getById(id);

        return ResponseEntity.ok(id1);
   }

    @GetMapping("/status/stats")
    public ResponseEntity<OrderStatusStatsDto> getStat() {

        OrderStatusStatsDto orderStatusStatsDto = wpOrderService.statusStats();
        return ResponseEntity.ok(orderStatusStatsDto);
    }

    @PostMapping("/sync/{siteId}")
    public void syncWpOrder(@PathVariable Long siteId){
            wpOrderService.syncOrderToDB(siteId);
    }

    @PatchMapping("/patch")
    public ResponseEntity<?> patch(@RequestBody WpOrderDto wpOrderDto) {
        try {
            WpOrderDto wpOrder = wpOrderService.patchOrderMain(wpOrderDto);
            return  ResponseEntity.ok(wpOrder);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("Грешка при частично обновяване: " + e.getMessage());
        }
    }

//    @PostMapping("/save")
//    @Transactional
//    public ResponseEntity<WpOrderDto> saveWpOrder(@RequestBody WpOrderDto wpOrderDto){
//        Optional<WpOrderEntity> byId = wpOrderRepository.findById(wpOrderDto.getId());
//        if(byId.isPresent()){
//            byId.get().setStatus(wpOrderDto.getStatus());
//
//            if (wpOrderDto.getOrdersToMerge() != null && !wpOrderDto.getOrdersToMerge().isEmpty()) {
//                WpOrderEntity mainOrder = byId.get();
//
//                // 1. Вземаме съществуващите продукти и правим НОВ списък (за JSONB Dirty Checking)
//                List<OrderLineItem> updatedLines = new ArrayList<>(mainOrder.getOrderLine());
//
//                List<WpOrderEntity> ordersToMerge = wpOrderRepository.findAllById(wpOrderDto.getOrdersToMerge());
//
//                for (WpOrderEntity subOrder : ordersToMerge) {
//                    // Пропускаме, ако поръчката вече е била обединена
//                    if (subOrder.getStatus() == OrderStatus.JOINT) continue;
//
//                    for (OrderLineItem oldItem : subOrder.getOrderLine()) {
//                        OrderLineItem newItem = new OrderLineItem();
//                        newItem.setProductName(oldItem.getProductName());
//                        newItem.setQuantity(oldItem.getQuantity());
//                        newItem.setSku(oldItem.getSku());
//                        newItem.setImage(oldItem.getImage());
//                        newItem.setPaoIdValue(oldItem.getPaoIdValue());
//                        newItem.setPrice(oldItem.getPrice());
//                        newItem.setTotalPrice(oldItem.getTotalPrice());
//                        newItem.setProductId(oldItem.getProductId()); // Не забравяй ID-то на продукта
//
//                        // Добавяме към новия списък
//                        updatedLines.add(newItem);
//
//                        // Обновяваме тотала на главната поръчка
//                        if (newItem.getTotalPrice() != null) {
//                            if (mainOrder.getTotalPrice() == null) mainOrder.setTotalPrice(BigDecimal.ZERO);
//                            mainOrder.setTotalPrice(mainOrder.getTotalPrice().add(newItem.getTotalPrice()));
//                        }
//                    }
//
//                    // 2. Маркираме под-поръчката като обединена
//                    subOrder.setStatus(OrderStatus.JOINT);
//                    subOrder.setParentId(mainOrder.getId());
//                    wpOrderRepository.save(subOrder);
//                }
//
//                // 3. Сетваме НОВИЯ списък в главната поръчка и записваме ВЕДНЪЖ
//                mainOrder.setOrderLine(updatedLines);
//                wpOrderRepository.save(mainOrder);
//
//                // Допълнителен лог за проверка
//                System.out.println("Main order " + mainOrder.getId() + " updated with " + updatedLines.size() + " items.");
//            }
//
//            WpOrderEntity order = byId.get();
//            order.setComment(wpOrderDto.getComment());
//            order.getBilling().setFirstName(wpOrderDto.getBilling().getFirstName());
//            order.getBilling().setLastName(wpOrderDto.getBilling().getLastName());
//            order.getBilling().setEmail(wpOrderDto.getBilling().getEmail());
//            order.getBilling().setPhone(wpOrderDto.getBilling().getPhone());
//            order.setTotalPrice(wpOrderDto.getTotalPrice());
//            order.setCustomShippingTotal(wpOrderDto.getCustomShippingTotal());
//            order.setPaymentMethod(wpOrderDto.getPaymentMethod());
//
//
//            if (!order.getOrderLine().equals(wpOrderDto.getOrderLine())) {
//                List<OrderLineItem> list = wpOrderDto.getOrderLine().stream().map(e -> {
//                    OrderLineItem map = modelMapper.map(e, OrderLineItem.class);
//                    map.setTotalPrice(e.getTotalPrice());
//                    map.setPrice(e.getTotalPrice());
//                    return map;
//                }).toList();
//                order.setOrderLine(list);
//            }
//
//            AtomicReference<BigDecimal> totalPrice = new AtomicReference<>(BigDecimal.ZERO);
//            order.getOrderLine().forEach(orderLineItem -> {
//                totalPrice.updateAndGet(v -> v.add(orderLineItem.getTotalPrice()));
//                // Използвай totalPrice на реда, за да хванеш Quantity * Price
//            });
//            order.setTotalPriceFCoutier(totalPrice.get());
//
//            if(!Objects.equals(wpOrderDto.getSite().getId(), order.getSite().getId())){
//                SiteEntity site = siteRepository.getReferenceById(wpOrderDto.getSite().getId());
//                order.setSite(site);
//            }
//
//            WpOrderEntity save = wpOrderRepository.save(byId.get());
//            if(save.getWpOrderId() != null){
//                wpOrderAsyncService.updateOrderOnSites(save, null);
//            }
////            notificationService.sendUpdate("orders", WsAction.UPDATED);
//            return ResponseEntity.ok(wpOrderDto);
//        }
//        return ResponseEntity.notFound().build();
//    }


    @PostMapping("/save")
    @Transactional
    public ResponseEntity<WpOrderDto> saveWpOrder(@RequestBody WpOrderDto wpOrderDto) {
        WpOrderDto wpOrderDto1 = wpOrderService.saveWpOrder(wpOrderDto);
        if(wpOrderDto1 != null) return ResponseEntity.ok(wpOrderDto1);
        else return ResponseEntity.notFound().build();
}

    @PostMapping("/create")
    public void createWpOrder(@RequestHeader("x-wc-webhook-signature") String signature,
                              @RequestBody String rawPayload){
//        System.out.println(rawPayload);
//    public void createWpOrder(
//                              @RequestBody String rawPayload){
//        System.out.println("d");
            Long siteId = webHookService.validateAndGetSiteId(rawPayload, signature);
//            Long siteId = 2L;
            if(siteId != null){
                try {
                    // 2. Ръчно десериализираме JSON-а към DTO
//                    ObjectMapper objectMapper = new ObjectMapper();
                    // Използваме ObjectMapper, защото rawPayload ни трябваше като String за проверката
                    WoOrderDto dto = objectMapper.readValue(rawPayload, WoOrderDto.class);
//                    System.out.println(dto.toString());
                    // 3. Извикваме сървиса за запис в базата
//                    System.out.println("1");
                    wpOrderService.newOrderFromSite(dto, siteId);
//                    System.out.println("2");
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
            log.info("TEST");
            rs = speedyService.createWayBill(createLabelDto);
        } else if(createLabelDto.getCourierType() == CourierType.BOX_NOW) {
            rs = boxNowService.createWayBill(createLabelDto);
        }
        if(rs instanceof Boolean && (boolean) rs) {
            Optional<WpOrderEntity> byId1 = wpOrderRepository.findById(createLabelDto.getId());
            if(byId1.isPresent()){
                WpOrderEntity order = byId1.get();
                order.setStatus(OrderStatus.SENT);
                wpOrderRepository.saveAndFlush(order);
                wpOrderAsyncService.updateOrderOnSites(order, null);
            }

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

    @PostMapping(value = "/signal/{orderId}", consumes = "text/plain")
    public ResponseEntity<?> createSignal(@PathVariable Long orderId, @RequestBody String text) {
        try {
            if (text == null || text.isBlank()) {
                return ResponseEntity.badRequest().body("Текстът не може да е празен");
            }
            boolean result = nekorektenService.sendReport(orderId, text);
            return ResponseEntity.ok(Map.of("success", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
