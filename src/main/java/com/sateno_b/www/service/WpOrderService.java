package com.sateno_b.www.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.entity.data.PaoIdValue;
import com.sateno_b.www.model.entity.data.PaoIdValueValue;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.enums.ProductSaleType;
import com.sateno_b.www.model.enums.TaskType;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.shared.AuthTool;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WpOrderService {

    private final WpOrderRepository wpOrderRepository;
    private final RestClient restClient;
    private final SiteRepository siteRepository;
    private final ObjectMapper objectMapper;
    @PersistenceContext
    private final EntityManager entityManager;
    private final WpProductService wpProductService;
    private final NekorektenService nekorektenService;
    private final ModelMapper modelMapper;

    private static final String ORDER_URL = "/wp-json/wc/v3/orders/";
    private final CustomerRepository customerRepository;
    private final UserSignalRepository userSignalRepository;
    private final EmailService emailService;
    private final OrderAutomationService orderAutomationService;
    private final WpProductRepository wpProductRepository;
    private final WpProductHistoryRepository wpProductHistoryRepository;


    public void syncOrderToDB(Long siteId){
        SiteEntity site = siteRepository.findById(siteId).orElse(null);
        if(site == null) {return;}




        List<WoOrderDto> all = fetchAllOrders(site);

        saveAllToDb(all, siteId);

    }

    @Transactional
    protected void saveAllToDb(List<WoOrderDto> orders, Long siteId) {
        int count = 0;
        for (WoOrderDto dto : orders) {

            if(wpOrderRepository.existsByWpOrderId(dto.getId())) {
                continue;
            }

            String rawPhone = dto.getBilling().getPhone().replaceAll("[^0-9]", "");
            String phoneSuffix = rawPhone.length() >= 9
                    ? rawPhone.substring(rawPhone.length() - 9)
                    : rawPhone;

            CustomerEntity customer = customerRepository.findByPhoneSuffix(phoneSuffix)
                    .stream()
                    .findFirst()
                    .orElseGet(() -> {
                        CustomerEntity customerEntity = new CustomerEntity();
                        customerEntity.setPhone(dto.getBilling().getPhone());
                        customerEntity.setFirstName(dto.getBilling().getFirstName());
                        customerEntity.setLastName(dto.getBilling().getLastName());
                        customerEntity.setEmail(dto.getBilling().getEmail());
                        customerEntity.setAddress(dto.getBilling().getAddress1().isEmpty()
                                ? dto.getBilling().getAddress2()
                                : dto.getBilling().getAddress1());
                        customerEntity.setEik(dto.getBilling().getCompanyName());
                        return customerRepository.save(customerEntity);
                    });

            SiteEntity siteEntity = siteRepository.getReferenceById(siteId);


            AtomicReference<Double> totalPriceRs = new AtomicReference<>(0.0);
            WpOrderEntity wpOrderEntity = new WpOrderEntity();
            wpOrderEntity.setCustomer(customer);
            wpOrderEntity.setSite(siteEntity);
            wpOrderEntity.setWpOrderId(dto.getId());
            wpOrderEntity.setOrderLine(dto.getLineItems()
                    .stream()
                    .map(woOrderLineItemDto -> {
                        totalPriceRs.updateAndGet(v -> v + Double.parseDouble(woOrderLineItemDto.getTotal().toString()));
                        OrderLineItem orderLineItem = new OrderLineItem();
                        orderLineItem.setSku(woOrderLineItemDto.getSku());
                        orderLineItem.setQuantity(woOrderLineItemDto.getQuantity());
                        orderLineItem.setPrice(woOrderLineItemDto.getPrice());
                        orderLineItem.setProductId(woOrderLineItemDto.getProductId());
                        orderLineItem.setProductName(woOrderLineItemDto.getProductName());
                        orderLineItem.setTotalPrice(woOrderLineItemDto.getTotal());
                        orderLineItem.setImage(woOrderLineItemDto.getImage());
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
            wpOrderEntity.setTotalPrice(new BigDecimal(totalPriceRs.get()));
            wpOrderEntity.setPaymentMethod(dto.getPaymentMethod());
            wpOrderEntity.setTransactionId(dto.getTransactionId());
            wpOrderEntity.setShippingLines(dto.getShippingLines());
            LocalDateTime ldt = LocalDateTime.parse(dto.getDateCreated());
            Instant instant = ldt.atZone(ZoneId.of("Europe/Sofia")).toInstant();
            wpOrderEntity.setWpOrderTime(instant);
            wpOrderRepository.save(wpOrderEntity);
            if (++count == 50) {
                count = 0;
                wpOrderRepository.flush();
                entityManager.clear();
            }
        }
    }

    @Transactional
    public void newOrderFromSite(WoOrderDto dto, Long siteId) {

        Optional<WpOrderEntity> byWpOrderId = wpOrderRepository.findByWpOrderId(dto.getId());
        if(byWpOrderId.isPresent()) {
            return;
        }

        String rawPhone = dto.getBilling().getPhone().replaceAll("[^0-9]", "");
        String phoneSuffix = rawPhone.length() >= 9
                ? rawPhone.substring(rawPhone.length() - 9)
                : rawPhone;


        CustomerEntity customer = customerRepository.findByPhoneSuffix(phoneSuffix)
                .stream()
                .findFirst()
                .orElseGet(CustomerEntity::new);

//        CustomerEntity customer = customerRepository.findByPhoneSuffix(phoneSuffix)
//                .stream()
//                .findFirst()
//                .orElseGet(() -> {
//                    CustomerEntity customerEntity = new CustomerEntity();
//                    customerEntity.setPhone(dto.getBilling().getPhone());
//                    customerEntity.setFirstName(dto.getBilling().getFirstName());
//                    customerEntity.setLastName(dto.getBilling().getLastName());
//                    customerEntity.setEmail(dto.getBilling().getEmail());
//                    customerEntity.setAddress(dto.getBilling().getAddress1().isEmpty()
//                            ? dto.getBilling().getAddress2()
//                            : dto.getBilling().getAddress1());
//                    customerEntity.setEik(dto.getBilling().getCompanyName());
//                    return customerRepository.save(customerEntity);
//                });
        customer.setPhone(dto.getBilling().getPhone());
        customer.setFirstName(dto.getBilling().getFirstName());
        customer.setLastName(dto.getBilling().getLastName());
        customer.setEmail(dto.getBilling().getEmail());
        customer.setAddress(dto.getBilling().getAddress1().isEmpty()
                            ? dto.getBilling().getAddress2()
                            : dto.getBilling().getAddress1());
        customer.setEik(dto.getBilling().getCompanyName());
        customer = customerRepository.save(customer);

        SiteEntity siteEntity = siteRepository.findById(siteId).get();


        AtomicReference<Double> totalPriceRs = new AtomicReference<>(0.0);
        WpOrderEntity wpOrderEntity = new WpOrderEntity();
        wpOrderEntity.setCustomer(customer);
        wpOrderEntity.setSite(siteEntity);
        wpOrderEntity.setWpOrderId(dto.getId());
        wpOrderEntity.setOrderLine(dto.getLineItems()
                .stream()
                .map(woOrderLineItemDto -> {
                    totalPriceRs.updateAndGet(v -> v + Double.parseDouble(woOrderLineItemDto.getTotal().toString()));
                    OrderLineItem orderLineItem = new OrderLineItem();
                    orderLineItem.setSku(woOrderLineItemDto.getSku());
                    orderLineItem.setQuantity(woOrderLineItemDto.getQuantity());
                    orderLineItem.setPrice(woOrderLineItemDto.getPrice());
                    orderLineItem.setProductId(woOrderLineItemDto.getProductId());
                    orderLineItem.setProductName(woOrderLineItemDto.getProductName());
                    orderLineItem.setTotalPrice(woOrderLineItemDto.getTotal());
                    orderLineItem.setImage(woOrderLineItemDto.getImage());
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

                    WooProductDto product = wpProductService.getProductId(siteEntity, woOrderLineItemDto.getProductId());
                    orderLineItem.setWeight(product.getWeight());
                    orderLineItem.setDimensions(product.getDimensions());
                    return orderLineItem;
                }).toList());
        wpOrderEntity.setBilling(dto.getBilling());
        wpOrderEntity.setShipping(dto.getShipping());
        wpOrderEntity.setCurrency(dto.getCurrency());
        wpOrderEntity.setCurrency_symbol(dto.getCurrencySymbol());
        wpOrderEntity.setStatus(dto.getStatus());
        wpOrderEntity.setCustomerIp(dto.getCustomerIpAddress());
        wpOrderEntity.setCustomerAgent(dto.getCustomerUserAgent());
        wpOrderEntity.setTotalPrice(new BigDecimal(totalPriceRs.get()));
        wpOrderEntity.setPaymentMethod(dto.getPaymentMethod());
        wpOrderEntity.setTransactionId(dto.getTransactionId());
        wpOrderEntity.setShippingLines(dto.getShippingLines());
        LocalDateTime ldt = LocalDateTime.parse(dto.getDateCreated());
        Instant instant = ldt.atZone(ZoneId.of("Europe/Sofia")).toInstant();
        wpOrderEntity.setWpOrderTime(instant);

        NekorektenResponseDto nekorektenResponseDto = nekorektenService.checkPhone(rawPhone);
        if(nekorektenResponseDto != null) {
//
            for (NekorektenResponseDto.NekorektenItemDto item : nekorektenResponseDto.getItems()) {
                UserSignalEntity userSignalEntity = new UserSignalEntity();
                userSignalEntity.setCreateDate(item.getCreateDate());
                userSignalEntity.setFirstName(item.getFirstName());
                userSignalEntity.setLastName(item.getLastName());
                userSignalEntity.setText(item.getText());
                userSignalEntity.setCustomer(customer);
                userSignalRepository.save(userSignalEntity);
            }
        }
        if(siteEntity.getEmail() != null) {
            String subject = "Нова поръчка";

            EmailSendRequest emailSendRequest = new EmailSendRequest();
            emailSendRequest.setTo(wpOrderEntity.getCustomer().getEmail());
            emailSendRequest.setConfigId(siteEntity.getEmail().getId());
            emailSendRequest.setSubject(subject);
            emailSendRequest.setContent(siteEntity.getNewOrderMessage());
            emailSendRequest.setGenConfirm(true);
            emailSendRequest.setShowItemsTable(true);
            emailSendRequest.setWpOrderEntity(wpOrderEntity);
            EmailLogEntity emailLogEntity = emailService.sendEmail(emailSendRequest);
            if(emailLogEntity != null) {
                wpOrderEntity.getEmails().add(emailLogEntity);
            }


            if(siteEntity.getSecondOrderMessageTimer() != null && siteEntity.getSecondOrderMessageTimer() > 0){
                orderAutomationService.scheduleTask(wpOrderEntity, TaskType.SECOND_EMAIL, siteEntity.getSecondOrderMessageTimer());
            }
           if(siteEntity.getChangeStatusTimer() != null && siteEntity.getChangeStatusTimer() > 0){
               orderAutomationService.scheduleTask(wpOrderEntity, TaskType.STATUS_CHANGE, siteEntity.getChangeStatusTimer());
           }


        }

    wpOrderRepository.save(wpOrderEntity);

        for (WoOrderLineItemDto orderLineItem : dto.getLineItems()) {
            if(orderLineItem.getQuantity() > 0){
                Optional<WpProductEntity> byId = wpProductRepository.findBySku(orderLineItem.getSku());
                if(byId.isPresent()){
                    WpProductEntity product = byId.get();
                    WpProductHistoryEntity wpProductHistoryEntity = new WpProductHistoryEntity();

                    if(product.getStockQuantity() == null){
                        product.setStockQuantity(0);
                    }
                    product.setStockQuantity((product.getStockQuantity() - orderLineItem.getQuantity()));
                    wpProductHistoryEntity.setQuantity(orderLineItem.getQuantity());
//                    if(product.getSaleType() == ProductSaleType.UNLIMITED){
//                        product.setStockQuantity((product.getStockQuantity() - orderLineItem.getQuantity()));
//                        wpProductHistoryEntity.setQuantity(orderLineItem.getQuantity());
//                    } else if(product.getSaleType() == ProductSaleType.LIMITED && product.getStockQuantity() >= 0 &&
//                            product.getStockQuantity() >= orderLineItem.getQuantity()) {
//                        product.setStockQuantity((product.getStockQuantity() - orderLineItem.getQuantity()));
//                        wpProductHistoryEntity.setQuantity(orderLineItem.getQuantity());
//                    }
                    if(wpProductHistoryEntity.getQuantity() != null && wpProductHistoryEntity.getQuantity() > 0){
                        wpProductHistoryEntity.setProduct(product);
                        wpProductHistoryEntity.setOrder(wpOrderEntity);
                        wpProductHistoryEntity.setReason("Order");
                        wpProductHistoryRepository.save(wpProductHistoryEntity);
                    }
                wpProductRepository.save(product);
                    wpProductService.updateProductOnSites(product, wpOrderEntity.getSite().getId());
                }
            }
        }
    }

    private List<WoOrderDto> fetchAllOrders(SiteEntity site){
        String auth = AuthTool.getAuth(site.getConsumerKey(), site.getConsumerSecret());
        List<WoOrderDto> allOrders = new ArrayList<>();

        int currentPage = 1;
        int totalPages = 1;

        do {

            var response = restClient.get()
                    .uri(site.getUrlWithHttps() + ORDER_URL + "?per_page=100&page=" + currentPage + "&orderby=id&order=desc")
                    .header("Authorization", "Basic " + auth)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<WoOrderDto>>() {});

            List<String> totalPagesHeader = response.getHeaders().get("X-WP-TotalPages");
//            System.out.println("totalPagesHeader: " + totalPagesHeader);
            if (totalPagesHeader != null && !totalPagesHeader.isEmpty()) {
                totalPages = Integer.parseInt(totalPagesHeader.get(0));
            }

            if(response.getBody() != null) {
                allOrders.addAll(response.getBody());
            }


            currentPage++;
        } while (currentPage <= totalPages);

//        System.out.println(allOrders.size());
        return allOrders;
    }


    @Transactional
    public Page<WpOrderDto> getAll(Pageable pageable, @RequestParam(required = false) String status,
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

                List<UserSignalEntity> byCustomerId = userSignalRepository.findByCustomerId(entity.getCustomer().getId());
                List<UserSignalDto> signalDtos = byCustomerId.stream().map(e ->  modelMapper.map(e, UserSignalDto.class)).toList();
                dto.setSignals(signalDtos);
            }

//            userSignalRepository.findByCustomerId(entity.getCustomer().getId())
//            long count = wpOrderRepository.countByCustomer(entity.getCustomer());
//            dto.setCustomerOrderCount(count);
//            System.out.println(count);

            List<EmailLogEntity> email = entity.getEmails();
//                    .stream().min(Comparator.comparing(EmailLogEntity::getCreateTime))
//                    .orElse(null);
            for (EmailLogEntity log : email) {
                if(log.isConfirmed()){
                    dto.setConfirmed(true);
                    break;
                }
            }

            return dto;
        });

        return wpOrderDtos;

    }


    public WpOrderDto patchOrder(WpOrderDto wpOrderDto) {
        Optional<WpOrderEntity> byId = wpOrderRepository.findById(wpOrderDto.getId());
        if(byId.isPresent()) {
            WpOrderEntity wpOrderEntity = byId.get();
            wpOrderEntity.setComment(wpOrderDto.getComment());
            wpOrderRepository.save(wpOrderEntity);
            return modelMapper.map(wpOrderEntity, WpOrderDto.class);
        }
        throw new RuntimeException("Order not found");
    }
}
