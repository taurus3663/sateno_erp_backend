package com.sateno_b.www.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sateno_b.www.model.dto.*;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.entity.data.CourierContractDetails;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.entity.data.WpOrderCourierHistory;
import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.enums.CourierType;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.interfaces.ShippingProvider;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpOrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class EcontService implements ShippingProvider {

    private final RestClient restClient;
    private final SiteRepository siteRepository;
    private final CourierSettingsRepository courierSettingsRepository;
    private final WpOrderRepository wpOrderRepository;

    //    @Override
//    public List<ShipmentCityDto> getCities(String username, String password, String nameFilter) {
//        Map<String, Object> body = new HashMap<>();
//        System.out.println(nameFilter);
//        body.put("countryCode", "BGR"); // ISO Alpha-3
//        body.put("start", 0);
//        body.put("count", 10);
//        body.put("name", nameFilter + "%");
//        body.put("city_name", nameFilter);
//
//        try {
//            Map<String, Object> response = postToEcont(
//                    "services/Nomenclatures/NomenclaturesService.getCities.json",
//                    body,
//                    username,
//                    password
//            );
//            Map<String, Object> response = getToEcont(
//                    "https://couriers.lynk.bg/new/bg-cities.txt"
//            );
//
//            System.out.println(response);
//
//            if (response == null) {
//                log.error("Econt getCities: null response");
//                return List.of();
//            }
//
//            List<?> sitesList = (List<?>) response.get("cities");
//
//            System.out.println(sitesList.size());
//
//            return sitesList.stream()
//                    .filter(item -> item instanceof Map)
//                    .map(item -> (Map<String, Object>) item)
//                    .filter(cityMap -> {
//                        if (nameFilter == null || nameFilter.isBlank()) {
//                            return true;
//                        }
//                        String cityName = (String) cityMap.get("name");
//                        return cityName != null &&
//                                cityName.toLowerCase().contains(nameFilter.toLowerCase());
//                 })
//                    .map(cityMap -> {
//                        ShipmentCityDto dto = new ShipmentCityDto();
//                        System.out.println(cityMap.toString());
//                        dto.setId(
//                                cityMap.get("id") != null
//                                        ? Long.parseLong(cityMap.get("id").toString())
//                                        : null
//                        );
//
//                        dto.setName((String) cityMap.getOrDefault("name", ""));
//
//                        dto.setPostCode(
//                                cityMap.get("postCode") != null
//                                        ? cityMap.get("postCode").toString()
//                                        : ""
//                        );
//
//                        return dto;
//                    })
//                    .toList();
//
//        } catch (Exception e) {
//            log.error("Econt getCities error", e);
//            return List.of();
//        }
//    }
    @Override
    public List<ShipmentCityDto> getCities(String username, String password, String nameFilter) {
    try {
        Map<String, Object> response = getToEcont("https://couriers.lynk.bg/new/bg-cities.txt");
        if (response == null) return List.of();

        // Филтрираме по nameFilter и flatten-нем структурата
        List<ShipmentCityDto> result = response.entrySet().stream()
                // филтър по име на града
                .filter(entry -> nameFilter == null || nameFilter.isBlank() ||
                        entry.getKey().toLowerCase().contains(nameFilter.toLowerCase()))
                // flatten zipMap
                .flatMap(entry -> {
                    Map<String, Object> zipMap = (Map<String, Object>) entry.getValue();
                    return zipMap.values().stream()
                            .flatMap(listObj -> ((List<Map<String, Object>>) listObj).stream());
                })
                // map към DTO
                .map(c -> {
                    ShipmentCityDto dto = new ShipmentCityDto();
                    dto.setId(Long.parseLong(c.get("ext_id").toString()));
                    dto.setName((String) c.get("name"));
                    dto.setPostCode((String) c.get("zip"));
                    return dto;
                })
                // лимит до 10
                .limit(10)
                .toList();

        return result;

    } catch (Exception e) {
        log.error("Econt getCities error", e);
        return List.of();
    }
}

    @Override
    public List<ShipmentOfficeDto> getOffices(String username, String password, Long cityId, String nameFilter) {
        Map<String, Object> body = new HashMap<>();
        body.put("cityID", cityId);
        body.put("name", nameFilter);

        try {
            Map<String, Object> response = postToEcont("services/Nomenclatures/NomenclaturesService.getOffices.json", body, username, password);

//            System.out.println(response);
            if (response == null || !response.containsKey("offices")) {
                return List.of();
            }

            List<?> officesList = (List<?>) response.get("offices");
            return officesList.stream()
                    .map(item -> (Map<String, Object>) item)
                    .map(o -> {
//                        System.out.println(o.toString());
                        ShipmentOfficeDto dto = new ShipmentOfficeDto();
                        dto.setId(Long.parseLong(o.get("id").toString()));
                        dto.setName((String) o.get("name"));
                        dto.setCode(Long.parseLong(o.get("code").toString()));

                        Map<String, Object> addressMap = (Map<String, Object>) o.get("address");
                        if (addressMap != null) {
                            dto.setAddress((String) addressMap.get("fullAddress"));
                        }
                        return dto;
                    })
                    .toList();
        } catch (Exception e) {
            log.error("Econt getOffices error: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean createWayBill(CreateLabelDto createLabelDto) {
        CourierSettingsEntity courierSettingsEntity = courierSettingsRepository.findById(createLabelDto.getCourierId()).get();
        var contract = getContract(courierSettingsEntity.getUsername(), courierSettingsEntity.getPassword());
//        System.out.println(contract.toString());
//        System.out.println(createLabelDto.toString());
        WpOrderEntity order = wpOrderRepository.findById(createLabelDto.getId()).get();

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> label =  new HashMap<>();

//        SENDER
        Map<String, Object> senderClient = new HashMap<>();
        senderClient.put("name", contract.getClientName());
//        senderClient.put("id", contract.getClientId());
//        senderClient.put("authorizedPerson", contract.getClientName());
        senderClient.put("phones", List.of(courierSettingsEntity.getConfig().getPhoneNumber()));
//        senderClient.put("phones", List.of("0877706656"));
//        senderClient.put("phone", contract.getPhones().get(0).getNumber());
//        senderClient.put("phone", "0877706656");
        label.put("senderClient",  senderClient);

        Map<String, Object> senderAgent = new HashMap<>();
        senderAgent.put("name", courierSettingsEntity.getConfig().getAgentName());
        senderAgent.put("phones", List.of(courierSettingsEntity.getConfig().getPhoneNumber()));
        label.put("senderAgent",  senderAgent);

        Map<String, Object> senderAddress = new HashMap<>();
        Map<String, Object> city = new HashMap<>();
        city.put("country", Map.of("code3", "BGR"));
        city.put("name", courierSettingsEntity.getConfig().getCity());
        city.put("postCode", courierSettingsEntity.getConfig().getPostalCode());
//        city.put("id", contract.getAddress().getSiteId());
        senderAddress.put("city", city);
        senderAddress.put("street", courierSettingsEntity.getConfig().getAddress());
        label.put("senderAddress",  senderAddress);

//        RECEIVER
        Map<String, Object> receiverClient = new HashMap<>();
        receiverClient.put("name", order.getBilling().getFirstName() + " " + order.getBilling().getLastName());
        receiverClient.put("phones", List.of(order.getBilling().getPhone()));
        label.put("receiverClient", receiverClient);



        Map<String, Object> receiverAddress = new HashMap<>();
        Map<String, Object> receiverCity = new HashMap<>();
//        System.out.println(createLabelDto.getCourierShipmentType());
        if (createLabelDto.getCourierShipmentType() == CourierShipmentType.OFFICE ||
                createLabelDto.getCourierShipmentType() == CourierShipmentType.LOCKER) {
//            System.out.println("here");
            label.put("receiverOfficeCode", createLabelDto.getOffice().getCode());
//            receiverCity.put("id", createLabelDto.getCity().getId());
//            receiverAddress.put("street", createLabelDto.getOffice().getAddress());
        } else {
            receiverCity.put("country", Map.of("code3", "BGR"));
            receiverCity.put("name", createLabelDto.getCity().getName());
            receiverCity.put("postCode", createLabelDto.getCity().getPostCode());
//            receiverCity.put("id", createLabelDto.getOffice().getId());
            receiverAddress.put("street", createLabelDto.getStreet());
//            receiverAddress.put("num", "9");
//            receiverAddress.put("other", "bl. 5");
            receiverAddress.put("city", receiverCity);
            label.put("receiverAddress", receiverAddress);
        }
//        label.put("sendDate", toString());
        label.put("holidayDeliveryDay", "workday");

        label.put("packCount", createLabelDto.getPackCount());
        label.put("shipmentType", "PACK");
        label.put("weight", createLabelDto.getWeight());
//        label.put("shipmentDescription", "Текстилни изделия");
        label.put("shipmentDescription", String.join(
                ", ",
                order.getOrderLine()
                        .stream()
                        .map(OrderLineItem::getProductName)
                        .toList()
        ));
        label.put("packingListType", "digital");


        List<Map<String, Object>> packingList = new ArrayList<>();
//        double calculatedSum = 0.0;
        for (OrderLineItem orderLineItem : order.getOrderLine()) {
            Map<String, Object> item = new HashMap<>();

            // Номер на артикула (SKU или ID)
            item.put("inventoryNum", orderLineItem.getSku() != null ? orderLineItem.getSku() : String.valueOf(orderLineItem.getProductId()));

            // Име на продукта (това, което ще види клиента)
            item.put("description", orderLineItem.getProductName());

            // Тегло на конкретния артикул (в кг)
            double itemWeight = (orderLineItem.getWeight() != null && !orderLineItem.getWeight().isEmpty())
                    ? Double.parseDouble(orderLineItem.getWeight()) : 0.5;
            item.put("weight", itemWeight);



            // Цена на единична бройка
            item.put("price", Double.parseDouble(orderLineItem.getPrice().toString()));

            // Количество
            item.put("count", orderLineItem.getQuantity());

            packingList.add(item);
//            calculatedSum += (Long.parseLong(orderLineItem.getPrice().toString()) * orderLineItem.getQuantity());
        }
        Map<String, Object> shippingPrice = new HashMap<>();
        shippingPrice.put("inventoryNum", "SHIPPING-1");
        shippingPrice.put("description", "Доставка");
        shippingPrice.put("weight", 0.5);
        shippingPrice.put("price", order.getCustomShippingTotal());
        shippingPrice.put("count", 1);
        packingList.add(shippingPrice);
        label.put("packingList", packingList);


        Map<String, Object> services = new HashMap<>();

        services.put("cdAmount", Double.parseDouble(order.getTotalPriceFCoutier().toString()) + order.getCustomShippingTotal());
        services.put("cdCurrency", order.getCurrency());
        services.put("cdType", "get"); // 'get' е стандартната стойност за събиране на сумата
        services.put("cdPayOptionsTemplate", "CD257894");
// 2. Слагаме services в label
        label.put("services", services);

// 3. Други важни полета от спецификацията:
        label.put("paymentReceiverMethod", "sender"); // cash Получателят плаща в брой
        label.put("sendDate", LocalDate.now(ZoneId.of("Europe/Sofia")).toString());
        label.put("holidayDeliveryDay", "workday");

// 4. Опция "Преглед" (по желание)
        label.put("payAfterAccept", true);
        label.put("payAfterTest", true);

        Map<String, Object> returnParams = new HashMap<>();
//        returnParams.put("rejectAction", "return"); // Какво да се прави при отказ
        returnParams.put("rejectOriginalParcelPaySide", "receiver"); // Кой плаща отиването
        returnParams.put("rejectReturnParcelPaySide", "receiver");   // Кой плаща връщането
        returnParams.put("rejectInstruction", "return");
// Можеш да добавиш и това от схемата, ако искаш автоматично връщане при непотърсена пратка
//        returnParams.put("daysUntilReturn", 7);

// 2. Опаковаме ги в обекта Instruction
        Map<String, Object> instruction = new HashMap<>();
        instruction.put("type", "return");
        instruction.put("returnInstructionParams", returnParams); // ТУК СЕ ВЛАГАТ ПАРАМЕТРИТЕ

// 3. Слагаме масива в лейбъла
        label.put("instructions", List.of(instruction));


        body.put("label", label);
        body.put("mode", "create");

//        Map<String, Object> stringObjectMap = postToEcont("services/ClientService.getPaymentOptions.json", body, courierSettingsEntity.getUsername(), courierSettingsEntity.getPassword());
//        System.out.println(stringObjectMap.toString());

//        System.out.println(label.toString());
        Map<String, Object> response = postToEcont("services/Shipments/LabelService.createLabel.json", body, courierSettingsEntity.getUsername(), courierSettingsEntity.getPassword());
//        System.out.println(response);

        EcontCreateLabelResponse labelResponse = getLabelResponse(response);
        order.setWayBillUrl(labelResponse.getLabel().getPdfURL());
        order.setWayBillShipmentNumber(Long.parseLong(labelResponse.getLabel().getShipmentNumber()));
        order.setCourierType(CourierType.ECONT);
        order.setCourierId(createLabelDto.getCourierId());
        wpOrderRepository.save(order);
        return true;
    }

    public boolean cancelShipment(WpOrderEntity order, CourierSettingsEntity settings) {
        // Вземаме номера на товарителницата
        String waybillNumber = order.getWayBillShipmentNumber().toString();

        if (order.getWayBillShipmentNumber() == null || waybillNumber.isEmpty()) {
            throw new RuntimeException("Липсва номер на товарителница за анулиране към Еконт.");
        }

        try {
            // Подготвяме тялото на заявката според документацията на Еконт
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("shipmentNumbers", List.of(waybillNumber));

            // Примерно извикване към Еконт API (обикновено /services/Shipments/LabelService.deleteLabels)
            Map<String, Object> response = postToEcont("/services/Shipments/LabelService.deleteLabels.json", requestBody, settings.getUsername(), settings.getPassword());
            System.out.println(response);
            // Проверка за грешки в самия отговор (Еконт често връщат грешките вътре в JSON-а)
            if (response.containsKey("error") || response.containsKey("errors")) {
                String errorMsg = response.get("error") != null ? response.get("error").toString() : "Грешка при анулиране в Еконт";
                throw new RuntimeException(errorMsg);
            }

            // Ако е успешно, чистим локалните данни
            order.setWayBillShipmentNumber(null);
            order.setWayBillUrl(null);
            order.getParcelIds().clear();
            order.setCourierType(null);
            order.setStatus(OrderStatus.PROCESSING);
            order.setCourierType(null);
            order.setCourierId(null);

            wpOrderRepository.save(order);
            // WebSocket сигналът ще се изстреля автоматично от @PostUpdate в Entity-то
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Еконт отказ: " + e.getMessage());
        }
    }

//    public byte[] getWaybillPdf(List<String> parcelList, String paperSize, String username, String password, WpOrderEntity order) {
//        byte[] pdfBytes = null;
//
//        String url = order.getWayBillUrl();
//
//        pdfBytes = restClient.get()
//                .uri(url)
//                .headers(httpHeaders -> {
//                    httpHeaders.setBasicAuth(username, password);
//                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
//                })
//                .retrieve()
//                .onStatus(HttpStatusCode::isError, (request, response) -> {
//                    // Логваме ако Econt върне 4xx или 5xx
//                    System.err.println("Econt Print Error Status: " + response.getStatusCode());
//                })
//                .body(byte[].class);
//
//        return pdfBytes;
//    }

    private EcontCreateLabelResponse getLabelResponse(Map<String, Object> response) {
        ObjectMapper mapper = new ObjectMapper();
        // Конвертира Map директно към твоя DTO клас
        return mapper.convertValue(response, EcontCreateLabelResponse.class);
    }

    public boolean testLogin(String username, String password, Long courierId) {
        try {
            var response = postToEcont("services/Profile/ProfileService.getClientProfiles.json", new HashMap<>(), username, password);

            List<?> profilesList = (List<?>) response.get("profiles");
            if (profilesList != null && !profilesList.isEmpty()) {
                // 1. Взимаме първия профил
                Map<String, Object> profileMap = (Map<String, Object>) profilesList.get(0);

                // 2. Данните за клиента са вложени в ключ "client"
                Map<String, Object> clientMap = (Map<String, Object>) profileMap.get("client");

                if (clientMap != null) {
                    CourierContractDetails details = new CourierContractDetails();

                    // Мапваме ID-то (1488680848) и името
                    details.setClientId(Long.parseLong(clientMap.get("id").toString()));
                    details.setClientName((String) clientMap.get("name"));

                    // Еконт не връща "objectName" директно тук, ползваме името на фирмата
                    details.setObjectName((String) clientMap.get("name"));

                    // Ползваме molName за контактно лице
                    details.setContactName((String) clientMap.get("molName"));

                    // Имейлът е в основния client обект
                    details.setEmail(clientMap.get("email") != null ? clientMap.get("email").toString() : "");

                    // 3. Обработка на адреса (взимаме първия от списъка "addresses")
                    List<?> addressesList = (List<?>) profileMap.get("addresses");
                    if (addressesList != null && !addressesList.isEmpty()) {
                        Map<String, Object> addrMap = (Map<String, Object>) addressesList.get(0);
                        Map<String, Object> cityMap = (Map<String, Object>) addrMap.get("city");

                        CourierContractDetails.AddressDetails addr = new CourierContractDetails.AddressDetails();
                        if (cityMap != null) {
                            addr.setSiteId(Long.parseLong(cityMap.get("id").toString()));
                            addr.setSiteName((String) cityMap.get("name"));
                            addr.setPostCode(cityMap.get("postCode") != null ? cityMap.get("postCode").toString() : "");
                        }

                        // Сглобяваме адрес за визуализация
                        String fullAddr = (String) addrMap.get("street") + " " + (String) addrMap.get("num");
                        addr.setFullAddressString(fullAddr);

                        details.setAddress(addr);
                    }

                    // 4. Запис в базата
//                    CourierSettingsEntity c = courierSettingsRepository.findById(courierId).orElse(null);
//                    if (c != null) {
//                        c.setCourierContractDetails(details);
//                        courierSettingsRepository.save(c);
//                    }
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("EcontService testLogin error: {}", e.getMessage());
            return false;
        }
    }


public double calculatePrice(CheckCourierRequest createLabelDto) {
  try {
    SiteEntity site = siteRepository.findSiteEntityByUrl(createLabelDto.getSite());
    CourierSettingsEntity courierSettingsEntity = courierSettingsRepository.findBySiteAndCourierTypeAndActiveTrueAndDefaultCourierTrue(site, createLabelDto.getCourierType()).get();
    var contract = getContract(courierSettingsEntity.getUsername(), courierSettingsEntity.getPassword());
//        System.out.println(contract.toString());
//        System.out.println(createLabelDto.toString());
//    WpOrderEntity order = wpOrderRepository.findById(createLabelDto.get()).get();

    Map<String, Object> body = new HashMap<>();
    Map<String, Object> label =  new HashMap<>();

//        SENDER
    Map<String, Object> senderClient = new HashMap<>();
    senderClient.put("name", contract.getClientName());
//        senderClient.put("id", contract.getClientId());
//        senderClient.put("authorizedPerson", contract.getClientName());
    senderClient.put("phones", List.of(courierSettingsEntity.getConfig().getPhoneNumber()));
//        senderClient.put("phones", List.of("0877706656"));
//        senderClient.put("phone", contract.getPhones().get(0).getNumber());
//        senderClient.put("phone", "0877706656");
    label.put("senderClient",  senderClient);

    Map<String, Object> senderAgent = new HashMap<>();
    senderAgent.put("name", courierSettingsEntity.getConfig().getAgentName());
    senderAgent.put("phones", List.of(courierSettingsEntity.getConfig().getPhoneNumber()));
    label.put("senderAgent",  senderAgent);

    Map<String, Object> senderAddress = new HashMap<>();
    Map<String, Object> city = new HashMap<>();
    city.put("country", Map.of("code3", "BGR"));
    city.put("name", courierSettingsEntity.getConfig().getCity());
    city.put("postCode", courierSettingsEntity.getConfig().getPostalCode());
//        city.put("id", contract.getAddress().getSiteId());
    senderAddress.put("city", city);
    senderAddress.put("street", courierSettingsEntity.getConfig().getAddress());
    label.put("senderAddress",  senderAddress);

//        RECEIVER
    Map<String, Object> receiverClient = new HashMap<>();
    receiverClient.put("name", "ТЕСТ" + " " + "ТЕСТ");
    receiverClient.put("phones", List.of());
    label.put("receiverClient", receiverClient);



    Map<String, Object> receiverAddress = new HashMap<>();
    Map<String, Object> receiverCity = new HashMap<>();
//        System.out.println(createLabelDto.getCourierShipmentType());
    if (createLabelDto.getCourierShipmentType() == CourierShipmentType.OFFICE ||
            createLabelDto.getCourierShipmentType() == CourierShipmentType.LOCKER) {
//            System.out.println("here");
        label.put("receiverOfficeCode", createLabelDto.getTargetId());
//            receiverCity.put("id", createLabelDto.getCity().getId());
//            receiverAddress.put("street", createLabelDto.getOffice().getAddress());
    }
    else {
        receiverCity.put("country", Map.of("code3", "BGR"));
        receiverCity.put("name", createLabelDto.getCityName());
        receiverCity.put("postCode", createLabelDto.getPostcode());
//        receiverAddress.put("street", createLabelDto.getStreet());
//            receiverAddress.put("num", "9");
//            receiverAddress.put("other", "bl. 5");
        receiverAddress.put("city", receiverCity);
        label.put("receiverAddress", receiverAddress);
    }
//        label.put("sendDate", toString());
    label.put("holidayDeliveryDay", "workday");

    label.put("packCount", createLabelDto.getItems().size());
    label.put("shipmentType", "PACK");
    label.put("weight", createLabelDto.getCart_weight());
//        label.put("shipmentDescription", "Текстилни изделия");
    label.put("shipmentDescription", String.join(
            ", ",
            createLabelDto.getItems()
                    .stream()
                    .map(CheckOutCourierItemsDto::getName)
                    .toList()
    ));
    label.put("packingListType", "digital");


    List<Map<String, Object>> packingList = new ArrayList<>();
//        double calculatedSum = 0.0;
    for (CheckOutCourierItemsDto orderLineItem : createLabelDto.getItems()) {
        Map<String, Object> item = new HashMap<>();

        // Номер на артикула (SKU или ID)
        item.put("inventoryNum", orderLineItem.getSku() != null ? orderLineItem.getSku() : String.valueOf(orderLineItem.getId()));

        // Име на продукта (това, което ще види клиента)
        item.put("description", orderLineItem.getName());

        // Тегло на конкретния артикул (в кг)
        double itemWeight = (orderLineItem.getWeight() != null)
                ? orderLineItem.getWeight() : 0.5;
        item.put("weight", itemWeight);



        // Цена на единична бройка
        item.put("price", Double.parseDouble(orderLineItem.getPrice().toString()));

        // Количество
        item.put("count", orderLineItem.getQuantity());

        packingList.add(item);
//            calculatedSum += (Long.parseLong(orderLineItem.getPrice().toString()) * orderLineItem.getQuantity());
    }
    label.put("packingList", packingList);


    Map<String, Object> services = new HashMap<>();

    services.put("cdAmount", createLabelDto.getCart_total());
    services.put("cdCurrency", createLabelDto.getCurrency());
    services.put("cdPaySide", "receiver");
    services.put("cdType", "get"); // 'get' е стандартната стойност за събиране на сумата
    services.put("cdPayOptionsTemplate", "CD257894");
// 2. Слагаме services в label
    label.put("services", services);
      label.put("paySide", "receiver");

// 3. Други важни полета от спецификацията:
    label.put("paymentReceiverMethod", "cash"); // Получателят плаща в брой
    label.put("sendDate", LocalDate.now(ZoneId.of("Europe/Sofia")).toString());
    label.put("holidayDeliveryDay", "workday");

// 4. Опция "Преглед" (по желание)
    label.put("payAfterAccept", true);
    label.put("payAfterTest", true);

    Map<String, Object> returnParams = new HashMap<>();
//        returnParams.put("rejectAction", "return"); // Какво да се прави при отказ
    returnParams.put("rejectOriginalParcelPaySide", "receiver"); // Кой плаща отиването
    returnParams.put("rejectReturnParcelPaySide", "receiver");   // Кой плаща връщането
    returnParams.put("rejectInstruction", "return");
// Можеш да добавиш и това от схемата, ако искаш автоматично връщане при непотърсена пратка
//        returnParams.put("daysUntilReturn", 7);

// 2. Опаковаме ги в обекта Instruction
    Map<String, Object> instruction = new HashMap<>();
    instruction.put("type", "return");
    instruction.put("returnInstructionParams", returnParams); // ТУК СЕ ВЛАГАТ ПАРАМЕТРИТЕ

// 3. Слагаме масива в лейбъла
    label.put("instructions", List.of(instruction));


    body.put("label", label);
    body.put("mode", "calculate");

//        Map<String, Object> stringObjectMap = postToEcont("services/ClientService.getPaymentOptions.json", body, courierSettingsEntity.getUsername(), courierSettingsEntity.getPassword());
//        System.out.println(stringObjectMap.toString());

//        System.out.println(label.toString());
    Map<String, Object> response = postToEcont("services/Shipments/LabelService.createLabel.json", body, courierSettingsEntity.getUsername(), courierSettingsEntity.getPassword());
//        System.out.println(response);

      if (response != null && response.containsKey("label")) {
                    Map<String, Object> labelRes = (Map<String, Object>) response.get("label");

                    // Стойността, която ПОЛУЧАТЕЛЯТ трябва да плати
                    if (labelRes.containsKey("receiverDueAmount")) {
                        double due = Double.parseDouble(labelRes.get("receiverDueAmount").toString());
                        if (due > 0) {
                            return due; // Връщаме цената за клиента
                        }
                    }

                    // Fallback към общата цена
                    if (labelRes.containsKey("totalPrice")) {
                        return Double.parseDouble(labelRes.get("totalPrice").toString());
                    }
      }
  } catch (Exception e) {
            log.error("Econt Error: {}", e.getMessage());
        }
        return -1.0;
}

    public double calculatePriceDefault(double weight, CourierShipmentType type) {
        double basePrice = 0;

        if (type == CourierShipmentType.OFFICE) {
            if (weight <= 3) basePrice = 4.05;
            else if (weight <= 5) basePrice = 4.88;
            else if (weight <= 10) basePrice = 6.44;
            else basePrice = 6.44 + (weight - 10) * 0.50;
        } else if (type == CourierShipmentType.ADDRESS) { // До адрес
            if (weight <= 3) basePrice = 5.95;
            else if (weight <= 5) basePrice = 7.20;
            else if (weight <= 10) basePrice = 10.50;
            else basePrice = 10.50 + (weight - 10) * 0.80;
        } else if ( type == CourierShipmentType.LOCKER ) {
            if (weight <= 3) basePrice = 3.20; // Примерна цена за малък пакет
            else if (weight <= 20) basePrice = 4.50;
            else basePrice = 6.00;
        }

        // Добавяме средна такса гориво (напр. 10%) и ДДС (20%)
        double fuelSurcharge = 1.10;
        return basePrice * fuelSurcharge * 1.20;
    }

    private Map<String, Object> postToEcont(String endpoint, Map<String, Object> body, String username, String password) {

        String turl = "https://ee.econt.com/" + endpoint;
//        String turl = "https://demo.econt.com/ee/" + endpoint;
        return restClient.post()
                .uri(turl)
//                .uri("https://ee.econt.com/ee/" + endpoint)
                .headers(httpHeaders -> {
                    httpHeaders.setBasicAuth(username, password);
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(body)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

    }
    private Map<String, Object> getToEcont(String endpoint) {
        String responseStr = restClient.get()
                .uri(endpoint)
                .retrieve()
                .body(String.class); // първо като String

        try {
            // след това превръщаме текста в Map чрез Jackson
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(responseStr, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error parsing JSON from Econt: {}", e.getMessage());
            return Map.of();
        }
    }
    private CourierContractDetails getContract(String username, String password) {
        var response = postToEcont("services/Profile/ProfileService.getClientProfiles.json", new HashMap<>(), username, password);
//        System.out.println("00000000000000000000000000000000000000000000000000000000000");
//        System.out.println(response);
//        System.out.println("00000000000000000000000000000000000000000000000000000000000");
        List<?> profilesList = (List<?>) response.get("profiles");
//        System.out.println(profilesList.size());
//        for (Object o : profilesList) {
//            System.out.println("1111111111111111111111111111");
//            System.out.println(o.toString());
//            System.out.println("1111111111111111111111111111");
//        }
//        System.out.println(profilesList.size());
        if (profilesList != null && !profilesList.isEmpty()) {

            // 1. Взимаме първия профил
            Map<String, Object> profileMap = (Map<String, Object>) profilesList.get(0);
//            System.out.println(profileMap);
//            System.out.println("00000000000000000");
            // 2. Данните за клиента са вложени в ключ "client"
            Map<String, Object> clientMap = (Map<String, Object>) profileMap.get("client");
//            System.out.println(clientMap.toString());
            if (clientMap != null) {
                CourierContractDetails details = new CourierContractDetails();

                // Мапваме ID-то (1488680848) и името
                details.setClientId(Long.parseLong(clientMap.get("id").toString()));
                details.setClientName((String) clientMap.get("name"));

                // Еконт не връща "objectName" директно тук, ползваме името на фирмата
//                details.setObjectName((String) clientMap.get("objectName"));

                // Ползваме molName за контактно лице
                details.setContactName((String) clientMap.get("molName"));

                // Имейлът е в основния client обект
                details.setEmail(clientMap.get("email") != null ? clientMap.get("email").toString() : "");

                List<CourierContractDetails.PhoneDetails> phoneDetails = new ArrayList<>();
                for (String phones : (List<String>) clientMap.get("phones")) {
                    CourierContractDetails.PhoneDetails phoneDetail = new CourierContractDetails.PhoneDetails();
                    phoneDetail.setNumber(phones);
                    phoneDetails.add(phoneDetail);
                }
                details.setPhones(phoneDetails);

                // 3. Обработка на адреса (взимаме първия от списъка "addresses")
                List<?> addressesList = (List<?>) profileMap.get("addresses");
                if (addressesList != null && !addressesList.isEmpty()) {
                    Map<String, Object> addrMap = (Map<String, Object>) addressesList.get(0);
                    Map<String, Object> cityMap = (Map<String, Object>) addrMap.get("city");

                    CourierContractDetails.AddressDetails addr = new CourierContractDetails.AddressDetails();
                    if (cityMap != null) {
                        addr.setSiteId(Long.parseLong(cityMap.get("id").toString()));
                        addr.setSiteName((String) cityMap.get("name"));
                        addr.setPostCode(cityMap.get("postCode") != null ? cityMap.get("postCode").toString() : "");

                    }

                    // Сглобяваме адрес за визуализация
                    String fullAddr = (String) addrMap.get("street") + " " + (String) addrMap.get("num");
                    addr.setNum(addrMap.get("num") != null ? addrMap.get("num").toString() : "");
                    addr.setFullAddressString(fullAddr);


                    details.setAddress(addr);
                }
//                System.out.println("111111111111111111111111111111111111111111111111111111");
//                System.out.println(details);
//                System.out.println("111111111111111111111111111111111111111111111111111111");
                return details;
            }

        }
        return null;
    }

    @Scheduled(fixedRate = 10 * 60 * 1000)
    @Transactional
    protected void sheckShipments() {
        List<WpOrderEntity> allByCourierTypeAndStatusSent = wpOrderRepository.findAllByCourierTypeAndStatus(CourierType.ECONT, OrderStatus.SENT);

        Map<Long, List<WpOrderEntity>> ordersBySite = allByCourierTypeAndStatusSent.stream()
                .collect(Collectors.groupingBy(order -> order.getSite().getId()));



        for (Map.Entry<Long, List<WpOrderEntity>> entry : ordersBySite.entrySet()) {
            Long siteId = entry.getKey();
            List<WpOrderEntity> siteOrders = entry.getValue();

            // 4. Вземаме настройките за Еконт за конкретния сайт
            CourierSettingsEntity settings = courierSettingsRepository
                    .findBySiteIdAndCourierTypeAndActiveTrue(siteId, CourierType.ECONT)
                    .orElse(null);

            if (settings == null) continue;

            // 5. Събираме номерата на товарителниците (wayBillShipmentNumber)
            List<String> waybillNumbers = siteOrders.stream()
                    .map(order -> order.getWayBillShipmentNumber().toString())
                    .toList();

            if (waybillNumbers.isEmpty()) continue;

//            List<String> waybillNumbers = List.of("1055101154014", "1055101141069");
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("shipmentNumbers", waybillNumbers);

         try {
             var response = postToEcont("services/Shipments/ShipmentService.getShipmentStatuses.json", requestBody, settings.getUsername(), settings.getPassword());
             processStatuses(response, siteOrders);
         }  catch (Exception e) {
             log.error("Error parsing JSON from Econt: {}", e.getMessage());
         }

        }


    }

    private void processStatuses(Object response, List<WpOrderEntity> siteOrders) {
        if (!(response instanceof Map)) return;
        Map<String, Object> resMap = (Map<String, Object>) response;
        List<Map<String, Object>> statuses = (List<Map<String, Object>>) resMap.get("shipmentStatuses");
        if (statuses == null) return;

        for (Map<String, Object> item : statuses) {
            Map<String, Object> statusInfo = (Map<String, Object>) item.get("status");
            if (statusInfo == null) continue;

            String shipmentNum = statusInfo.get("shipmentNumber").toString();
            List<Map<String, Object>> events = (List<Map<String, Object>>) statusInfo.get("trackingEvents");

            if (events == null || events.isEmpty()) continue;

            siteOrders.stream()
                    .filter(o -> o.getWayBillShipmentNumber() != null &&
                            o.getWayBillShipmentNumber().toString().equals(shipmentNum))
                    .findFirst()
                    .ifPresent(order -> {
                        if (order.getCourierHistory() == null) {
                            order.setCourierHistory(new ArrayList<>());
                        }

                        boolean isUpdated = false;

                        // ОБХОЖДАМЕ ЦЯЛАТА ХРОНОЛОГИЯ ОТ ЕКОНТ
                        for (Map<String, Object> event : events) {
                            String desc = (String) event.get("destinationDetails");
                            Long eventMillis = (Long) event.get("time");
                            Instant eventTime = Instant.ofEpochMilli(eventMillis);

                            // Проверка дали точно този ивент (време + текст) вече съществува
                            boolean alreadyExists = order.getCourierHistory().stream()
                                    .anyMatch(h -> h.getEventTime().equals(eventTime) &&
                                            h.getStatusDescription().equals(desc));

                            if (!alreadyExists) {
                                WpOrderCourierHistory newEntry = new WpOrderCourierHistory();
                                newEntry.setStatusDescription(desc);
                                newEntry.setEventTime(eventTime);

                                order.getCourierHistory().add(newEntry);
                                isUpdated = true;
                            }
                        }

                        // Ако сме добавили нови записи в историята, записваме поръчката веднъж
                        if (isUpdated) {
                            // Можеш тук да обновиш и главния статус на поръчката спрямо shortDeliveryStatus
//                            String currentShortStatus = (String) statusInfo.get("shortDeliveryStatus");
//                            updateOrderMainStatus(order, currentShortStatus, statusInfo.get("deliveryTime"));

                            wpOrderRepository.save(order);
                        }
                    });
        }
    }




}
