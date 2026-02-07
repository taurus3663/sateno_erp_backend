package com.sateno_b.www.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.WoOrderDto;
import com.sateno_b.www.model.dto.WpOrderDto;
import com.sateno_b.www.model.entity.WpOrderEntity;
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

import java.util.Optional;

@RestController
@RequestMapping("/wp_order")
@RequiredArgsConstructor
@Slf4j
public class WpOrderController {

    private final WpOrderRepository wpOrderRepository;
    private final ModelMapper modelMapper;
    private final WpOrderService wpOrderService;
    private final WebHookService webHookService;

    @GetMapping("/list")
    public ResponseEntity<Page<WpOrderDto>> getAll(Pageable pageable, @RequestParam(required = false) String status) {

        Pageable sortedByIdDesc = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("id").descending()
        );


        // 1. Конфигурираме мачера (как да сравнява)
//        ExampleMatcher matcher = ExampleMatcher.matching()
//                .withIgnoreNullValues() // Игнорира полетата, които не са пратени от Angular
//                .withIgnoreCase()      // Игнорира малки/големи букви
//                .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING); // LIKE %value% за текстове

        OrderStatus orderStatus = (status != null) ? OrderStatus.fromValue(status) : null;

        Page<WpOrderEntity> wpOrderEntities = wpOrderRepository.findWithFilters(orderStatus, sortedByIdDesc);
        Page<WpOrderDto> wpOrderDtos =wpOrderEntities.map(mapper -> modelMapper.map(mapper, WpOrderDto.class));

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
                    ObjectMapper objectMapper = new ObjectMapper();
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
