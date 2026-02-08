package com.sateno_b.www.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.OrderLineItemDto;
import com.sateno_b.www.model.dto.WoOrderDto;
import com.sateno_b.www.model.dto.WoOrderLineItemDto;
import com.sateno_b.www.model.dto.WpOrderDto;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.repository.WpOrderRepository;
import com.sateno_b.www.service.WebHookService;
import com.sateno_b.www.service.WpOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
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

    @GetMapping("/list")
    public ResponseEntity<Page<WpOrderDto>> getAll(Pageable pageable, @RequestParam(required = false) String status) {

        Pageable sortedByIdDesc = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("wpOrderTime").descending() // Първо по най-нова дата
                        .and(Sort.by("id").descending()) // После по ID, ако датите са еднакви
        );


        OrderStatus orderStatus = (status != null) ? OrderStatus.fromValue(status) : null;

        Page<WpOrderEntity> wpOrderEntities = wpOrderRepository.findWithFilters(orderStatus, sortedByIdDesc);
        Page<WpOrderDto> wpOrderDtos =wpOrderEntities.map(entity -> {
            WpOrderDto dto = modelMapper.map(entity, WpOrderDto.class);

            if(entity.getStatus() == OrderStatus.PROCESSING && entity.getCustomer() != null) {

                String phone = entity.getCustomer().getPhone();
                if(phone != null && !phone.isEmpty()) {
                    List<WpOrderEntity> duplicates = wpOrderRepository.findDuplicatesWithLines(
                            phone, OrderStatus.PROCESSING, entity.getId()
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

            return dto;
        });

        return ResponseEntity.ok(wpOrderDtos);
    }

    @PostMapping("/sync/{siteId}")
    public void syncWpOrder(@PathVariable Long siteId){
            wpOrderService.syncOrderToDB(siteId);
    }

    @PostMapping("/save")
    public ResponseEntity<WpOrderDto> saveWpOrder(@RequestBody WpOrderDto wpOrderDto){
        Optional<WpOrderEntity> byId = wpOrderRepository.findById(wpOrderDto.getId());
        if(byId.isPresent()){
            byId.get().setStatus(wpOrderDto.getStatus());
            wpOrderRepository.save(byId.get());
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

                    log.info("Успешно обработена поръчка #{} от сайт ID: {}", dto.getId(), siteId);


                } catch (JsonProcessingException e) {
                    log.error("Грешка при парсване на JSON от WooCommerce: {}", e.getMessage());

                } catch (Exception e) {
                    log.error("Грешка при обработка на поръчката: {}", e.getMessage());

                }
            }

    }
}
