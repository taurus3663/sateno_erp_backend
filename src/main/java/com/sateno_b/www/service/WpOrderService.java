package com.sateno_b.www.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.*;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.entity.data.PaoIdValue;
import com.sateno_b.www.model.entity.data.PaoIdValueValue;
import com.sateno_b.www.model.enums.*;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.shared.AuthTool;
import com.sateno_b.www.shared.CourierParser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
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
    private final WpProductAsyncService wpProductAsyncService;
    private final EcontService econtService;
    private final SpeedyService speedyService;
    private final BoxNowService boxnowService;
    private final CourierSettingsRepository courierSettingsRepository;
    private final WpOrderAsyncService wpOrderAsyncService;



    public void syncOrderToDB(Long siteId) {
        SiteEntity site = siteRepository.findById(siteId).orElse(null);
        if(site == null) {return;}

        List<WoOrderDto> all = fetchAllOrders(site);

        for (WoOrderDto dto : all) {


                try {
                    saveAllToDb(dto, siteId);
                } catch (Exception e) {
                    log.error("ERROR saving order id: {}", dto.getId(), e);
                }
        }
    }


    public void saveAllToDb(WoOrderDto dto, Long siteId) {
//        int count = 0;
//        List<Long> nulls = new ArrayList<>();


            if(wpOrderRepository.existsByWpOrderId(dto.getId())) {
                return;
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

                AtomicReference<BigDecimal> totalPrice = new AtomicReference<>(BigDecimal.ZERO);
                wpOrderEntity.getOrderLine().forEach(orderLineItem -> {
                    totalPrice.updateAndGet(v -> v.add(orderLineItem.getTotalPrice()));
                    // Използвай totalPrice на реда, за да хванеш Quantity * Price
                });
                wpOrderEntity.setTotalPriceFCoutier(totalPrice.get());
//            CourierParser.CourierMatch parse = CourierParser.parse(wpOrderEntity.getBilling().getAddress1());
//            if(parse == null) continue;
//            double totalWeight = 0;
//            List<CheckOutCourierItemsDto> items = new ArrayList<>();
//            for (OrderLineItem item : wpOrderEntity.getOrderLine()) {
//                totalWeight += Double.parseDouble(item.getWeight() != null? item.getWeight():"0.5");
//                CheckOutCourierItemsDto  checkOutCourierItemsDto = new CheckOutCourierItemsDto();
//                checkOutCourierItemsDto.setName("tt");
//                items.add(checkOutCourierItemsDto);
//            }
//            CheckCourierRequest request = new CheckCourierRequest();
//            request.setCart_total(totalPrice.get().toString());
//            request.setCart_weight(totalWeight);
//            request.setItems_count(String.valueOf(wpOrderEntity.getOrderLine().size()));
//            request.setItems(items);
//            request.setCurrency(wpOrderEntity.getCurrency());
//            if( !parse.getCode().isEmpty()){
//                request.setTargetId(parse.getCode());
//            }
//            request.setCityName("Медовец");
//            request.setPostcode("9238");
//            request.setCourierType(CourierType.valueOf(parse.getCourier()));
//            request.setCourierShipmentType(CourierShipmentType.valueOf(parse.getTargetType()));
//            request.setSite(siteEntity.getUrl());
//
//            double totalShipmentPrice = 0;
//
//            if(parse.getCourier().equals("BOXNOW")) {
//                totalShipmentPrice = boxnowService.calculatePrice(request);
//            } else if(parse.getCourier().equals("ECONT")) {
//
//                totalShipmentPrice = econtService.calculatePrice(request);
//
//            } else if(parse.getCourier().equals("SPEEDY")) {
//                totalShipmentPrice = speedyService.calculatePrice(request);
//            }

                CourierParser.CourierMatch parse = CourierParser.parseWithFallback(wpOrderEntity);
                if(parse != null) {
                    AtomicReference<Double> tPrice = new AtomicReference<>(0.0);
// Вземаме сумата на поръчката като double за сравнение
                    double orderAmount = wpOrderEntity.getTotalPrice().doubleValue();

// Корекция на името на куриера за Enum-а
                    String courierKey = parse.getCourier().equals("BOXNOW") ? "BOX_NOW" : parse.getCourier();

                    Optional<CourierSettingsEntity> allBySiteAndActive = courierSettingsRepository
                            .findBySiteAndCourierTypeAndActiveTrueAndDefaultCourierTrue(siteEntity, CourierType.valueOf(courierKey));

                    allBySiteAndActive.ifPresent(settings -> {
                        CourierShipmentType target = CourierShipmentType.valueOf(parse.getTargetType());

                        // --- 1. ДО ОФИС ---
                        if (target == CourierShipmentType.OFFICE && settings.isOffice()) {
                            if (settings.isOfficeFreeShippingPriceMaxBol() && settings.getOfficeFreeShippingPriceMax() != null && orderAmount >= settings.getOfficeFreeShippingPriceMax()) {
                                tPrice.set(0.0);
                            } else if (settings.isOfficeAutoShippingPrice()) {
                                tPrice.set(calculateAutoPrice(wpOrderEntity, parse, siteEntity));
                            } else {
                                tPrice.set(settings.getOfficeFixedShippingPrice() != null ? settings.getOfficeFixedShippingPrice() : 0.0);
                            }
                        }
                        // --- 2. ДО АВТОМАТ / LOCKER ---
                        else if (target == CourierShipmentType.LOCKER && settings.isLocker()) {
                            if (settings.isLockerFreeShippingPriceMaxBol() && settings.getLockerFreeShippingPriceMax() != null && orderAmount >= settings.getLockerFreeShippingPriceMax()) {
                                tPrice.set(0.0);
                            } else if (settings.isLockerAutoShippingPrice()) {
                                tPrice.set(calculateAutoPrice(wpOrderEntity, parse, siteEntity));
                            } else {
                                tPrice.set(settings.getLockerFixedShippingPrice() != null ? settings.getLockerFixedShippingPrice() : 0.0);
                            }
                        }
                        // --- 3. ДО АДРЕС ---
                        else if (target == CourierShipmentType.ADDRESS && settings.isAddress()) {
                            if (settings.isAddressFreeShippingPriceMaxBol() && settings.getAddressFreeShippingPriceMax() != null && orderAmount >= settings.getAddressFreeShippingPriceMax()) {
                                tPrice.set(0.0);
                            } else if (settings.isAddressAutoShippingPrice()) {
                                tPrice.set(calculateAutoPrice(wpOrderEntity, parse, siteEntity));
                            } else {
                                tPrice.set(settings.getAddressFixedShippingPrice() != null ? settings.getAddressFixedShippingPrice() : 0.0);
                            }
                        }
                    });
                    wpOrderEntity.setCustomShippingTotal(tPrice.get());



                wpOrderRepository.save(wpOrderEntity);


//            if (++count == 50) {
//                count = 0;
//                wpOrderRepository.flush();
//                entityManager.clear();
//            }
        }
//        System.out.println("NULLS " + nulls);
    }

    private Double calculateAutoPrice(WpOrderEntity entity, CourierParser.CourierMatch parse, SiteEntity site) {
        CheckCourierRequest request = new CheckCourierRequest();
        // 1. Използваме totalPriceFCoutier, за да сме сигурни, че сумата е за ВСИЧКИ бройки
        request.setCart_total(entity.getTotalPriceFCoutier().toString());
        request.setCurrency(entity.getCurrency());
        request.setTargetId(parse.getCode());
        request.setCourierType(CourierType.valueOf(parse.getCourier().equals("BOXNOW") ? "BOX_NOW" : parse.getCourier()));
        request.setCourierShipmentType(CourierShipmentType.valueOf(parse.getTargetType()));
        request.setSite(site.getUrl());

        // Правилно броене на реалните пакети/артикули
        int totalItemsQty = entity.getOrderLine().stream().mapToInt(OrderLineItem::getQuantity).sum();
        request.setItems_count(String.valueOf(totalItemsQty));

        request.setCityName(parse.getCity());
        request.setPostcode(parse.getPostcode());

        List<CheckOutCourierItemsDto> items = new ArrayList<>();
        double totalWeightCalculated = 0;

        for (OrderLineItem item : entity.getOrderLine()) {
            double singleWeight = Double.parseDouble(item.getWeight() != null ? item.getWeight() : "0.5");
            int qty = item.getQuantity();

            // Натрупваме общото тегло (Тегло * Количество)
            totalWeightCalculated += (singleWeight * qty);

            CheckOutCourierItemsDto dto = new CheckOutCourierItemsDto();
            dto.setName(item.getProductName());
            dto.setPrice(item.getTotalPrice().doubleValue() / qty);
            dto.setQuantity((long) qty); // Твоят Long
            dto.setWeight(singleWeight); // ПРАВИЛНО: Double/double, не (long) 0!
            dto.setSku(item.getSku());
            items.add(dto);
        }

        // ВАЖНО: Слагаме изчисленото общо тегло и НЕ го презаписваме долу!
        request.setCart_weight(totalWeightCalculated);
        request.setItems(items);

        // ИЗТРИЙ СТАРИЯ STREAM ОТ ТУК (който презаписваше теглото)
        try {
            return switch (request.getCourierType()) {
                case ECONT -> econtService.calculatePrice(request);
                case SPEEDY -> speedyService.calculatePrice(request);
                case BOX_NOW -> boxnowService.calculatePrice(request);
                default -> 0.0;
            };
        } catch (Exception e) {
            log.error("API calculation failed: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
//    @CacheEvict(value = "ordersList", allEntries = true)
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

        boolean isPayed;
        if(dto.getPaymentMethod() != PaymentMethod.COD) {
            boolean stripeConfirmed = dto.getMetaData().stream()
                    .anyMatch(m -> "_wc_stripe_charge_status".equals(m.getKey()) && "succeeded".equals(m.getValue()));
            if(stripeConfirmed) {
                isPayed = true;
            } else {
                isPayed = false;
            }
        } else {
            isPayed = false;
        }

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
                    orderLineItem.setTotalPrice(isPayed ? BigDecimal.valueOf(0): woOrderLineItemDto.getTotal());
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
        wpOrderEntity.setTotalPrice(isPayed? new BigDecimal(0): new BigDecimal(totalPriceRs.get()));
        wpOrderEntity.setTotalPriceFCoutier(isPayed? new BigDecimal(0): new BigDecimal(totalPriceRs.get()));
        wpOrderEntity.setPaymentMethod(dto.getPaymentMethod());
        wpOrderEntity.setTransactionId(dto.getTransactionId());
        wpOrderEntity.setShippingLines(dto.getShippingLines());
        LocalDateTime ldt = LocalDateTime.parse(dto.getDateCreated());
        Instant instant = ldt.atZone(ZoneId.of("Europe/Sofia")).toInstant();
        wpOrderEntity.setWpOrderTime(instant);

        AtomicReference<BigDecimal> totalPrice = new AtomicReference<>(BigDecimal.ZERO);
        wpOrderEntity.getOrderLine().forEach(orderLineItem -> {
            totalPrice.updateAndGet(v -> v.add(orderLineItem.getTotalPrice()));
            // Използвай totalPrice на реда, за да хванеш Quantity * Price
        });
        wpOrderEntity.setTotalPriceFCoutier(isPayed? BigDecimal.valueOf(0): totalPrice.get());

        CourierParser.CourierMatch parse = CourierParser.parseWithFallback(wpOrderEntity);
        if(parse != null && !isPayed) {
            AtomicReference<Double> tPrice = new AtomicReference<>(0.0);
// Вземаме сумата на поръчката като double за сравнение
            double orderAmount = wpOrderEntity.getTotalPrice().doubleValue();

// Корекция на името на куриера за Enum-а
            String courierKey = parse.getCourier().equals("BOXNOW") ? "BOX_NOW" : parse.getCourier();

            Optional<CourierSettingsEntity> allBySiteAndActive = courierSettingsRepository
                    .findBySiteAndCourierTypeAndActiveTrueAndDefaultCourierTrue(siteEntity, CourierType.valueOf(courierKey));

            allBySiteAndActive.ifPresent(settings -> {
                CourierShipmentType target = CourierShipmentType.valueOf(parse.getTargetType());
                // --- 1. ДО ОФИС ---
                if (target == CourierShipmentType.OFFICE && settings.isOffice()) {
                    if (settings.isOfficeFreeShippingPriceMaxBol() && settings.getOfficeFreeShippingPriceMax() != null && orderAmount >= settings.getOfficeFreeShippingPriceMax()) {
                        tPrice.set(0.0);
                    } else if (settings.isOfficeAutoShippingPrice()) {
                        tPrice.set(calculateAutoPrice(wpOrderEntity, parse, siteEntity));
                    } else {
                        tPrice.set(settings.getOfficeFixedShippingPrice() != null ? settings.getOfficeFixedShippingPrice() : 0.0);
                    }
                }
                // --- 2. ДО АВТОМАТ / LOCKER ---
                else if (target == CourierShipmentType.LOCKER && settings.isLocker()) {
                    if (settings.isLockerFreeShippingPriceMaxBol() && settings.getLockerFreeShippingPriceMax() != null && orderAmount >= settings.getLockerFreeShippingPriceMax()) {
                        tPrice.set(0.0);
                    } else if (settings.isLockerAutoShippingPrice()) {
                        tPrice.set(calculateAutoPrice(wpOrderEntity, parse, siteEntity));
                    } else {
                        tPrice.set(settings.getLockerFixedShippingPrice() != null ? settings.getLockerFixedShippingPrice() : 0.0);
                    }
                }
                // --- 3. ДО АДРЕС ---
                else if (target == CourierShipmentType.ADDRESS && settings.isAddress()) {
                    if (settings.isAddressFreeShippingPriceMaxBol() && settings.getAddressFreeShippingPriceMax() != null && orderAmount >= settings.getAddressFreeShippingPriceMax()) {
                        tPrice.set(0.0);
                    } else if (settings.isAddressAutoShippingPrice()) {
                        tPrice.set(calculateAutoPrice(wpOrderEntity, parse, siteEntity));
                    } else {
                        tPrice.set(settings.getAddressFixedShippingPrice() != null ? settings.getAddressFixedShippingPrice() : 0.0);
                    }
                }
            });
            wpOrderEntity.setCustomShippingTotal(tPrice.get());
        }
        if(isPayed) {
            wpOrderEntity.setCustomShippingTotal(0.0);
        }

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
                    try {
                        wpProductAsyncService.updateProductOnSites(product, wpOrderEntity.getSite().getId());
                    } catch (Exception e) {}

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
//        } while (currentPage <= 1);

//        System.out.println(allOrders.size());
        return allOrders;
    }


    @Transactional
//    @Cacheable(value = "ordersList",
//            key = "{#pageable.pageNumber, #pageable.pageSize, #status, #phone, #customer}")
    public Page<WpOrderDto> getAll(Pageable pageable, @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String phone,
                                   @RequestParam(required = false) String customer,
                                   @RequestParam(required = false) Long id) {

//        Pageable sortedByIdDesc = PageRequest.of(
//                pageable.getPageNumber(),
//                pageable.getPageSize(),
//                Sort.by("wpOrderTime").descending() // Първо по най-нова дата
//                        .and(Sort.by("id").descending()) // После по ID, ако датите са еднакви
//        );
//
//
//        OrderStatus orderStatus = (status != null) ? OrderStatus.fromValue(status) : null;
//
//        Page<WpOrderEntity> wpOrderEntities = wpOrderRepository.findWithFilters(orderStatus, phone, customer, sortedByIdDesc);


        Specification<WpOrderEntity> spec = (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.isEmpty()) {
                OrderStatus orderStatus = OrderStatus.fromValue(status);
                predicates.add(cb.equal(root.get("status"), orderStatus));
            }

            // 1. Дефинираме Join-а в началото, за да го ползваме навсякъде
            Join<WpOrderEntity, CustomerEntity> customerJoin = null;

            if ((customer != null && !customer.isEmpty()) || (phone != null && !phone.isEmpty())) {
                customerJoin = root.join("customer", JoinType.LEFT); // Използваме LEFT JOIN за всеки случай
            }

// Филтър по име/фамилия
            if (customer != null && !customer.isEmpty() && customerJoin != null) {
                String pattern = "%" + customer.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(customerJoin.get("firstName")), pattern),
                        cb.like(cb.lower(customerJoin.get("lastName")), pattern),
                        cb.like(customerJoin.get("phone"), pattern) // Можеш да търсиш тел. и в общото поле за клиент
                ));
            }

// Филтър по телефон (специфичното поле за телефон)
            if (phone != null && !phone.isEmpty() && customerJoin != null) {
                predicates.add(cb.like(customerJoin.get("phone"), "%" + phone + "%"));
            }

            if(id != null){
                Predicate idInternal = cb.equal(root.get("id"), id);
                Predicate wpId = cb.equal(root.get("wpOrderId"), id);
                predicates.add(cb.or(idInternal, wpId));
            }

            query.orderBy(
                    cb.desc(root.get("wpOrderTime")),
                    cb.desc(root.get("id"))
            );

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<WpOrderEntity> wpOrderEntities = wpOrderRepository.findAll(spec, pageable);

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

//            List<EmailLogEntity> email = entity.getEmails();
//                    .stream().min(Comparator.comparing(EmailLogEntity::getCreateTime))
//                    .orElse(null);
//            for (EmailLogEntity log : email) {
//                if(log.isConfirmed()){
//                    dto.setConfirmed(true);
//                    break;
//                }
//            }
            dto.setSavedCourierBilling(entity.getSavedCourierBilling());
            dto.setCourierHistory(entity.getCourierHistory());

            return dto;
        });

        return wpOrderDtos;

    }

    public WpOrderDto getById(Long id) {

        Optional<WpOrderEntity> orderEntity = wpOrderRepository.findById(id);
        if(orderEntity.isPresent()) {
            WpOrderEntity entity = orderEntity.get();
            WpOrderDto dto = modelMapper.map(orderEntity.get(), WpOrderDto.class);
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


            dto.setSavedCourierBilling(entity.getSavedCourierBilling());
            dto.setCourierHistory(entity.getCourierHistory());
            return dto;
        }
        return null;
    }

    public OrderStatusStatsDto statusStats() {
        OrderStatusStatsDto dto = new OrderStatusStatsDto();
        List<Object[]> objects = wpOrderRepository.countOrdersByStatus();

        // 1. Вземаме инстанцията на мапата от DTO-то
        Map<OrderStatus, Long> map = dto.getOrderStatusMap();

        // 2. Инициализираме всички статуси с 0 (за да имаме пълна статистика)
        for (OrderStatus status : OrderStatus.values()) {
            map.put(status, 0L);
        }

        // 3. Обхождаме резултатите от базата и презаписваме бройките
        if (objects != null) {
            for (Object[] object : objects) {
                // object[0] е OrderStatus, object[1] е Long (бройката)
                if (object[0] instanceof OrderStatus status) {
                    Long count = (Long) object[1];
                    map.put(status, count);
//                    System.out.println("Status found: " + status + " | Count: " + count);
                }
            }
        }

        return dto;
    }


//    @CacheEvict(value = "ordersList", allEntries = true)
    public WpOrderDto patchOrder(WpOrderDto wpOrderDto) {
        Optional<WpOrderEntity> byId = wpOrderRepository.findById(wpOrderDto.getId());
        if(byId.isPresent()) {
            WpOrderEntity wpOrderEntity = byId.get();

            if(wpOrderDto.getComment() != null) {
                wpOrderEntity.setComment(wpOrderDto.getComment());
            }

            if(wpOrderDto.getSavedCourierBilling() != null) {
                wpOrderEntity.setSavedCourierBilling(wpOrderDto.getSavedCourierBilling());
            }

            if(wpOrderDto.getStatus() != null) {
                wpOrderEntity.setStatus(wpOrderDto.getStatus());
            }

            WpOrderEntity save = wpOrderRepository.save(wpOrderEntity);
            wpOrderAsyncService.updateOrderOnSites(save, null);
            return modelMapper.map(wpOrderEntity, WpOrderDto.class);
        }
        throw new RuntimeException("Order not found");
    }


    public double checkCustomShippingField(CheckCourierRequest request) {
        Optional<WpOrderEntity> orderEntity = wpOrderRepository.findById(request.getOrderId());
        AtomicReference<Double> totalPrice = new AtomicReference<>(0D);
        request.getItems().forEach(item -> {
            totalPrice.updateAndGet(v -> v + (item.getPrice() * item.getQuantity()));
        });


        SiteEntity site = siteRepository.findSiteEntityByUrl(request.getSite());
        Optional<CourierSettingsEntity> bySiteAndCourierTypeAndActiveTrueAndDefaultCourierTrue = courierSettingsRepository.findBySiteAndCourierTypeAndActiveTrueAndDefaultCourierTrue(site, request.getCourierType());

        AtomicReference<Double> tPrice = new AtomicReference<>(0.0);

        bySiteAndCourierTypeAndActiveTrueAndDefaultCourierTrue.ifPresent(courierSettings -> {
            CourierShipmentType target = request.getCourierShipmentType();
//            System.out.println("11111114");
//            System.out.println(orderEntity.isPresent());
//            CourierParser.CourierMatch parse = new CourierParser.CourierMatch(request.getCourierType().name(), request.getCourierShipmentType().name(), request.getTargetId(), "", request.getCityName(), request.getPostcode());
            CourierParser.CourierMatch parse = CourierParser.parseWithFallback(orderEntity.get());
//            System.out.println(request.toString());

//            System.out.println("11111113");
            if(target == CourierShipmentType.OFFICE && courierSettings.isOffice()) {
                if(courierSettings.isOfficeFreeShippingPriceMaxBol() && courierSettings.getOfficeFreeShippingPriceMax() != null && totalPrice.get() >= courierSettings.getOfficeFreeShippingPriceMax()) {
                    tPrice.set(0.0);
                } else if(courierSettings.isOfficeAutoShippingPrice()) {
                    tPrice.set(calculateAutoPrice(orderEntity.get(), parse, site));
                } else {
                    tPrice.set(courierSettings.getOfficeFixedShippingPrice() != null ? courierSettings.getOfficeFixedShippingPrice() : 0.0);
                }
            }
            else if(target == CourierShipmentType.LOCKER && courierSettings.isLocker()) {
                System.out.println("1111111");
                if(courierSettings.isLockerFreeShippingPriceMaxBol() && totalPrice.get() >= courierSettings.getLockerFreeShippingPriceMax()) {
                    tPrice.set(0.0);
                } else if(courierSettings.isLockerAutoShippingPrice()) {
                    tPrice.set(calculateAutoPrice(orderEntity.get(), parse, site));
                } else {
                    tPrice.set(courierSettings.getLockerFixedShippingPrice() != null?  courierSettings.getLockerFixedShippingPrice() : 0.0);
                }
                System.out.println("111111122");
            }
            else if(target == CourierShipmentType.ADDRESS && courierSettings.isAddress()) {
                if(courierSettings.isAddressFreeShippingPriceMaxBol() && totalPrice.get() >= courierSettings.getAddressFreeShippingPriceMax()) {
                    tPrice.set(0.0);
                } else if(courierSettings.isAddressAutoShippingPrice()) {
                    tPrice.set(calculateAutoPrice(orderEntity.get(), parse, site));
                } else {
                    tPrice.set(courierSettings.getAddressFixedShippingPrice() != null ? courierSettings.getAddressFixedShippingPrice() : 0.0);
                }
            }



        });

        return tPrice.get();
    }
}
