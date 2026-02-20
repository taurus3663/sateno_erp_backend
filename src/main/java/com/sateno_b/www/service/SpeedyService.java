package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.CheckCourierRequest;
import com.sateno_b.www.model.dto.CreateLabelDto;
import com.sateno_b.www.model.dto.ShipmentCityDto;
import com.sateno_b.www.model.dto.ShipmentOfficeDto;
import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.entity.data.CourierContractDetails;
import com.sateno_b.www.model.entity.data.OrderLineItem;
import com.sateno_b.www.model.enums.CourierShipmentType;
import com.sateno_b.www.model.interfaces.ShippingProvider;
import com.sateno_b.www.model.repository.CourierSettingsRepository;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.WpOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class SpeedyService implements ShippingProvider {

    private final RestClient restClient;
    private final CourierSettingsRepository courierSettingsRepository;
    private final SiteRepository siteRepository;
    private final WpOrderRepository wpOrderRepository;

    public double calculatePrice(CheckCourierRequest request) {

        SiteEntity site = siteRepository.findSiteEntityByUrl(request.getSite());

       Optional<CourierSettingsEntity> g =
               courierSettingsRepository
                       .findBySiteAndCourierTypeAndCourierShipmentTypeAndActiveTrue(site, request.getCourierType(),
                               request.getCourierShipmentType());

       if(g.isPresent()) {
           CourierSettingsEntity courierSettings = g.get();



           if(courierSettings.getFreeShippingPriceMaxBol() == true && courierSettings.getFreeShippingPriceMax() < Double.parseDouble(request.getCart_total())){
               return 0;
           }

           try {
               Map<String, Object> body = createBaseBody(courierSettings.getUsername(), courierSettings.getPassword());
               body.put("shippingDate", java.time.LocalDate.now().toString());

               Map<String, Object> content = new HashMap<>();
               content.put("contents", "Текстилни изделия");
               if(request.getCourierShipmentType() == CourierShipmentType.LOCKER){
                   content.put("parcelsCount", 1);
               } else {
                   content.put("parcelsCount", request.getItems().size());
               }

               content.put("totalWeight", request.getCart_weight());
               body.put("content", content);

               Map<String, Object> payment = new HashMap<>();
               payment.put("courierServicePayer", "RECIPIENT");
               payment.put("declaredValuePayer", "RECIPIENT");
               body.put("payment", payment);

               Map<String, Object> service = new HashMap<>();

               service.put("autoAdjustPickupDate", true);
//               service.put("payerType", 1);

               // 3. НАЛОЖЕН ПЛАТЕЖ (Cash on Delivery)
               Map<String, Object> additionalServices = new HashMap<>();
               Map<String, Object> cod = new HashMap<>();
               cod.put("amount", Double.parseDouble(request.getCart_total())); // Сумата за събиране
               cod.put("currency", "EUR");
               additionalServices.put("cod", cod);

               service.put("additionalServices", additionalServices);



               var contract = courierSettings.getCourierContractDetails();
               Map<String, Object> sender = new HashMap<>();
               sender.put("clientId", contract.getClientId());
               body.put("sender", sender);

               Map<String, Object> recipient = new HashMap<>();
               recipient.put("privatePerson", true);
               if (request.getCourierShipmentType() == CourierShipmentType.OFFICE ||
                       request.getCourierShipmentType() == CourierShipmentType.LOCKER) {
                   service.put("serviceIds", List.of(505L));

                   // ВАЖНО: За офиси и автомати targetId е ID на офиса
                   recipient.put("pickupOfficeId", Long.parseLong(request.getTargetId()));
               } else if (request.getCourierShipmentType() == CourierShipmentType.ADDRESS) {
                   service.put("serviceIds", List.of(505L));

                   // До адрес: Спиди предпочита да получи siteId вътре в addressLocation
                   Map<String, Object> addressLocation = new HashMap<>();
//                   addressLocation.put("siteId", Long.parseLong(request.getTargetId()));

                   // Ако имаш пощенски код в рекуеста, добави го за по-голяма точност
                   if (request.getPostcode() != null && !request.getPostcode().isEmpty()) {
                       addressLocation.put("postCode", request.getPostcode());
                       addressLocation.put("siteName", request.getTargetId());
                   }

                   recipient.put("addressLocation", addressLocation);
               }
//               recipient.put("siteId", request.getTargetId());
               body.put("recipient", recipient);
               body.put("service", service);

//               List<Map<String, Object>> parcels = new ArrayList<>();
               Map<String, Object> response = postToSpeedy("calculate", body);
//               System.out.println(response);
               if (response != null && response.containsKey("calculations")) {
                List<Map<String, Object>> calculations =
                        (List<Map<String, Object>>) response.get("calculations");

                if (!calculations.isEmpty()) {

                    Map<String, Object> calc = calculations.get(0);

                    if (calc.containsKey("error")) {
                        System.out.println("Speedy service error: " + calc.get("error"));
                        return 0.0;
                    }

                    Map<String, Object> price =
                            (Map<String, Object>) calc.get("price");

                    if (price != null && price.containsKey("total")) {
//                        return Double.parseDouble(price.get("total").toString());
//                        System.out.println(Double.parseDouble(price.get("total").toString()));
                        return Double.parseDouble(price.get("total").toString());
                    }
                }
            }


            return -1;


           } catch (Exception e) {
               System.out.println("Speedy service error: " + e.getMessage());
           }
       }



        return 0;
    }

    public boolean createWayBill(CreateLabelDto createLabelDto) {
        System.out.println(createLabelDto.toString());
        CourierSettingsEntity courierSettingsEntity = courierSettingsRepository.findById(createLabelDto.getCourierId()).get();
        WpOrderEntity order = wpOrderRepository.findById(createLabelDto.getId()).get();
        Map<String, Object> body = createBaseBody(courierSettingsEntity.getUsername(), courierSettingsEntity.getPassword());

        Map<String, Object> content = new HashMap<>();
        content.put("contents", order.getOrderLine().stream()
                .map(OrderLineItem::getProductName)
                .collect(Collectors.joining(", ")));
        content.put("totalWeight", createLabelDto.getWeight());
        content.put("parcelsCount", order.getOrderLine().size());
        content.put("package", "STANDARD");
        content.put("valueOfGoods", order.getTotalPrice());

        List<Map<String, Object>> parcels = new ArrayList<>();

        int i = 0;
        for (OrderLineItem orderLineItem : order.getOrderLine()) {
            Map<String, Object> p = new HashMap<>();
            p.put("seqNo", ++i);
            p.put("weight", orderLineItem.getWeight());
            p.put("ref1", orderLineItem.getProductName());
            p.put("ref2", orderLineItem.getSku());
            parcels.add(p);
        }
        body.put("parcels", parcels);
        body.put("content", content);






        Map<String, Object> payment = new HashMap<>();
        payment.put("courierServicePayer", "RECIPIENT");
        payment.put("declaredValuePayer", "RECIPIENT");
        body.put("payment", payment);

        Map<String, Object> service = new HashMap<>();
        service.put("autoAdjustPickupDate", true);


        Map<String, Object> cod = new HashMap<>();

        if(createLabelDto.getFiscalReceipt() == true){
        List<Map<String, Object>> fiscalReceiptItems = new ArrayList<>();
        double checkSum = 0.0;
        for (OrderLineItem item : order.getOrderLine()) {
            Map<String, Object> fiscalItem = new HashMap<>();
            String name = item.getProductName();
            fiscalItem.put("description", name.length() > 50 ? name.substring(0, 47) + "..." : name);
            fiscalItem.put("vatGroup", "Б"); // Стандартно 20% ДДС
            double itemTotalWithVat = Double.parseDouble(item.getPrice().toString()) * item.getQuantity();
            double itemTotalWithoutVat = itemTotalWithVat / 1.2;
            fiscalItem.put("amountWithVat", itemTotalWithVat);
            fiscalItem.put("amount", Math.round(itemTotalWithoutVat * 100.0) / 100.0);
            fiscalReceiptItems.add(fiscalItem);
            checkSum += itemTotalWithVat;
        }
        // 2. Добавяне на доставката като отделен ред, ако клиентът я плаща
        if (Double.parseDouble(order.getTotalPrice().toString()) > checkSum) {
            double deliveryPrice = Double.parseDouble(order.getTotalPrice().toString()) - checkSum;
            Map<String, Object> deliveryLine = new HashMap<>();
            deliveryLine.put("description", "Доставка");
            deliveryLine.put("vatGroup", "Б");
            deliveryLine.put("amountWithVat", deliveryPrice);
            deliveryLine.put("amount", Math.round((deliveryPrice / 1.2) * 100.0) / 100.0);
            fiscalReceiptItems.add(deliveryLine);
        }

            cod.put("fiscalReceiptItems", fiscalReceiptItems);
        }

        // 3. НАЛОЖЕН ПЛАТЕЖ (Cash on Delivery)
        Map<String, Object> additionalServices = new HashMap<>();

        cod.put("amount", order.getTotalPrice()); // Сумата за събиране
        cod.put("currency", order.getCurrency());
        cod.put("processingType", "CASH");

        additionalServices.put("cod", cod);

        Map<String, Object> declaredValue = new HashMap<>();
        declaredValue.put("amount", order.getTotalPrice());
        declaredValue.put("currency", order.getCurrency());

        additionalServices.put("declaredValue", declaredValue);

        Map<String, Object> obpd = new HashMap<>();
        obpd.put("option", "OPEN");   // само преглед (отваряне)
        obpd.put("payer", "RECIPIENT");
        obpd.put("returnShipmentServiceId", 505L);
        obpd.put("returnShipmentPayer", "RECIPIENT");

        if(createLabelDto.getCourierShipmentType() == CourierShipmentType.OFFICE){
            additionalServices.put("obpd", obpd);
        }


        service.put("additionalServices", additionalServices);

        body.put("service", service);

        var contract = getContract(courierSettingsEntity.getUsername(), courierSettingsEntity.getPassword());
        Map<String, Object> sender = new HashMap<>();
        sender.put("clientId", contract.getClientId());
        body.put("sender", sender);
        Map<String, Object> recipient = new HashMap<>();
        recipient.put("privatePerson", true);

        String fullName = order.getBilling().getFirstName() + " " + order.getBilling().getLastName();
        recipient.put("clientName", fullName);
        Map<String, Object> phone = new HashMap<>();
        phone.put("number", order.getBilling().getPhone());
        recipient.put("phone1", phone);
        service.put("serviceIds", List.of(505L));
        service.put("serviceId", 505L);

        if(createLabelDto.getCourierShipmentType() == CourierShipmentType.OFFICE ||
                createLabelDto.getCourierShipmentType() == CourierShipmentType.LOCKER) {
            recipient.put("pickupOfficeId", createLabelDto.getOffice().getId());
        } else if(createLabelDto.getCourierShipmentType() == CourierShipmentType.ADDRESS) {
            Map<String, Object> addressLocation = new HashMap<>();
            addressLocation.put("siteId", createLabelDto.getCity().getId());
            recipient.put("addressLocation", addressLocation);
            // 2️⃣ ADDRESS
            Map<String, Object> address = new HashMap<>();
            address.put("siteId", createLabelDto.getCity().getId());
            address.put("postCode", createLabelDto.getCity().getPostCode());
            address.put("additionalDetails", createLabelDto.getStreet());
            address.put("streetNo", " ");
            address.put("streetName", createLabelDto.getStreet());


            recipient.put("address", address);
        }
        body.put("recipient", recipient);
        body.put("shippingDate", LocalDate.now().toString());

        System.out.println(body.toString());

        Map<String, Object> response = postToSpeedy("shipment", body);
//        Map<String, Object> response = postToSpeedy("calculate", body);

        System.out.println(response);



        return true;
    }

//    public double calculatePrice(double weight, CourierShipmentType shipmentType, String username, String password, CourierSettingsEntity courierSettings) {
//        try {
//            Map<String, Object> body = createBaseBody(username, password);
//            var contract = courierSettings.getCourierContractDetails();
//            Map<String, Object> content = new HashMap<>();
//            content.put("contents", "Текстилни изделия");
//            content.put("parcelsCount", 1);   // <-- ТУК трябва да е
//            content.put("totalWeight", 2.5);
//            body.put("content", content);
//
//            Map<String, Object> payment = new HashMap<>();
//
//            payment.put("courierServicePayer", "SENDER");
//
//            payment.put("declaredValuePayer", "SENDER");
//
//            body.put("payment", payment);
//
//            body.put("parcelsCount", 1);
//
//            // 1. ОСНОВНИ ПАРАМЕТРИ
//            // Пробвай със serviceId 3 (Икономична), защото 502/505 понякога са забранени за нови акаунти
//            long serviceId = (shipmentType == CourierShipmentType.ADDRESS) ? 2L : 3L;
//
//            Map<String, Object> service = new HashMap<>();
//            service.put("serviceIds", List.of(505L)); // ✅ ТУК Е КЛЮЧОВОТО
//            service.put("autoAdjustPickupDate", true);
//
//            body.put("service", service);
//
//
//            body.put("shippingDate", java.time.LocalDate.now().toString());
//
//            // 2. ПОДАТЕЛ (Използваме clientId)
//            Map<String, Object> sender = new HashMap<>();
//            sender.put("clientId", contract.getClientId());
//            body.put("sender", sender);
//
//            // 3. ПОЛУЧАТЕЛ
//            Map<String, Object> recipient = new HashMap<>();
//            recipient.put("privatePerson", true);
//            recipient.put("siteId", 68134L); // София
//
//            if (shipmentType == CourierShipmentType.ADDRESS) {
//                recipient.put("addressLocation", Map.of("siteId", 68134L));
//            } else {
//                // Офис 10 е винаги валиден (Централна поща София)
//                recipient.put("pickupOfficeId", 10L);
//            }
//            body.put("recipient", recipient);
//
//            // 4. ПАКЕТИ (PARCELS) - Ключово за грешката "service.required"
//            double calcWeight = (weight <= 0) ? 1.0 : weight;
//
//            // Спиди изисква списък от обекти, всеки със 'size'
//            List<Map<String, Object>> parcels = new ArrayList<>();
//            Map<String, Object> parcel = new HashMap<>();
//            Map<String, Object> size = new HashMap<>();
//            size.put("weight", calcWeight);
//            parcel.put("size", size);
//            parcels.add(parcel);
//
//            body.put("parcels", parcels);
//
//            // 5. ДОПЪЛНИТЕЛНИ ПАРАМЕТРИ (Понякога критични)
//            // Указваме изрично, че е 1 пакет
//            body.put("totalWeight", calcWeight);
//
//            // API повикване
//            Map<String, Object> response = postToSpeedy("calculate", body);
//            System.out.println("DEBUG SPEEDY PAYLOAD: " + body);
//            System.out.println("DEBUG SPEEDY RESPONSE: " + response);
//
//            if (response != null && response.containsKey("calculations")) {
//                List<Map<String, Object>> calculations =
//                        (List<Map<String, Object>>) response.get("calculations");
//
//                if (!calculations.isEmpty()) {
//
//                    Map<String, Object> calc = calculations.get(0);
//
//                    if (calc.containsKey("error")) {
//                        System.out.println("Speedy service error: " + calc.get("error"));
//                        return 0.0;
//                    }
//
//                    Map<String, Object> price =
//                            (Map<String, Object>) calc.get("price");
//
//                    if (price != null && price.containsKey("total")) {
//                        return Double.parseDouble(price.get("total").toString());
//                    }
//                }
//            }
//
//        } catch (Exception e) {
//            System.err.println("Speedy Calc Error: " + e.getMessage());
//        }
//        return 0.00;
//    }

    private CourierContractDetails getContract(String username, String password) {
        Map<String, Object> body = createBaseBody(username, password);

        var response = postToSpeedy("client/contract", body);


        if(response != null && response.containsKey("clients")) {
            List<?> clientsList = (List<?>) response.get("clients");
            if (!clientsList.isEmpty()) {
                // Взимаме първия клиент (обикновено е един за тези данни)
                Map<String, Object> clientMap = (Map<String, Object>) clientsList.get(0);

                // Мапваме към нашето DTO (можеш да ползваш Jackson ObjectMapper за по-лесно)
                CourierContractDetails details = new CourierContractDetails();
                details.setClientId(Long.parseLong(clientMap.get("clientId").toString()));
                details.setClientName(clientMap.get("clientName").toString());
                details.setObjectName(clientMap.get("objectName").toString());
                details.setContactName(clientMap.get("contactName").toString());
                details.setEmail(clientMap.get("email") != null ? clientMap.get("email").toString() : "");

                // Мапване на адреса
                if (clientMap.containsKey("address")) {
                    Map<String, Object> addrMap = (Map<String, Object>) clientMap.get("address");
                    CourierContractDetails.AddressDetails addr = new CourierContractDetails.AddressDetails();
                    addr.setSiteId(Long.parseLong(addrMap.get("siteId").toString()));
                    addr.setSiteName(addrMap.get("siteName").toString());
                    addr.setFullAddressString(addrMap.get("fullAddressString").toString());
                    addr.setPostCode(addrMap.get("postCode") != null ? addrMap.get("postCode").toString() : "");
                    details.setAddress(addr);
                }
                return details;
//                CourierSettingsEntity c = courierSettingsRepository.findById(courierId).orElse(null);
//                if (c != null) {
//                    c.setCourierContractDetails(details);
//                    courierSettingsRepository.save(c);
//                }
            }
        }

            return null;

    }

    @Override
    public List<ShipmentCityDto> getCities(String username, String password, String nameFilter) {
        var body = createBaseBody(username, password);
        body.put("countryId", 100);
        body.put("name", nameFilter);

        Map<String, Object> response = postToSpeedy("location/site", body);
        if (response == null || !response.containsKey("sites")) {
            return List.of();
        }
        List<?> sitesList = (List<?>) response.get("sites");
        return sitesList.stream()
                .map(item -> (Map<String, Object>) item)
                .map(s -> {
                    ShipmentCityDto dto = new ShipmentCityDto();
                    dto.setId(Long.parseLong(s.get("id").toString()));
                    dto.setName(s.get("name").toString());
                    dto.setPostCode(s.get("postCode") != null ? s.get("postCode").toString() : "");
                    return dto;
                })
                .toList();
    }

    @Override
        public List<ShipmentOfficeDto> getOffices( String username, String password, Long cityId, String nameFilter) {
            var body = createBaseBody(username, password);
            body.put("siteId", cityId); // Филтрираме офисите по избрания град
            body.put("name", nameFilter);
//        System.out.println(body.toString());
//        System.out.println(cityId);
//        System.out.println(nameFilter);
        Map<String, Object> response = postToSpeedy("location/office", body);
//        System.out.println(response);
        if (response == null || !response.containsKey("offices")) {
            return List.of();
        }
        List<?> sitesList = (List<?>) response.get("offices");
        return sitesList.stream()
                .map(item -> (Map<String, Object>) item)
                .map(s -> {
                    ShipmentOfficeDto dto = new ShipmentOfficeDto();
                    dto.setId(Long.parseLong(s.get("id").toString()));
                    dto.setName(s.get("name").toString());

                    Map<String, Object> address = (Map<String, Object>) s.get("address");
                    dto.setAddress(address != null ? address.get("fullAddressString").toString() : "");

//                    dto.setPostCode(s.get("postCode") != null ? s.get("postCode").toString() : "");
                    return dto;
                })
                .toList();
        }

    public boolean testLogin(String username, String password, Long courierId) {
        try {
//            System.out.println("Тестване на връзка със Спиди за потребител: " + username);

            Map<String, Object> body = new HashMap<>();
            // ВАЖНО: Ключовете трябва да са точно userName и password
            body.put("userName", username);
            body.put("password", password);
            body.put("language", "BG");

            // За да тестваме дали са верни данните, просто искаме информация за държава България
            // Това е най-лекият метод за проверка на достъп.
//            var response = restClient.post()
////                    .uri("https://api.speedy.bg/v1/location/country") // ДОБАВИ ТОЗИ ПЪТ
//                    .uri("https://api.speedy.bg/v1/client/contract") // ДОБАВИ ТОЗИ ПЪТ
//                    .body(body)
//                    .retrieve()
//                    .toEntity(Object.class);
            var response =  postToSpeedy("client/contract", body);
            System.out.println(response);

            if (response != null && response.containsKey("clients")) {


                List<?> clientsList = (List<?>) response.get("clients");
                if (!clientsList.isEmpty()) {
                    // Взимаме първия клиент (обикновено е един за тези данни)
                    Map<String, Object> clientMap = (Map<String, Object>) clientsList.get(0);

                    // Мапваме към нашето DTO (можеш да ползваш Jackson ObjectMapper за по-лесно)
                    CourierContractDetails details = new CourierContractDetails();
                    details.setClientId(Long.parseLong(clientMap.get("clientId").toString()));
                    details.setClientName(clientMap.get("clientName").toString());
                    details.setObjectName(clientMap.get("objectName").toString());
                    details.setContactName(clientMap.get("contactName").toString());
                    details.setEmail(clientMap.get("email") != null ? clientMap.get("email").toString() : "");

                    // Мапване на адреса
                    if (clientMap.containsKey("address")) {
                        Map<String, Object> addrMap = (Map<String, Object>) clientMap.get("address");
                        CourierContractDetails.AddressDetails addr = new CourierContractDetails.AddressDetails();
                        addr.setSiteId(Long.parseLong(addrMap.get("siteId").toString()));
                        addr.setSiteName(addrMap.get("siteName").toString());
                        addr.setFullAddressString(addrMap.get("fullAddressString").toString());
                        addr.setPostCode(addrMap.get("postCode") != null ? addrMap.get("postCode").toString() : "");
                        details.setAddress(addr);
                    }

                    CourierSettingsEntity c = courierSettingsRepository.findById(courierId).orElse(null);
                    if (c != null) {
                        c.setCourierContractDetails(details);
                        courierSettingsRepository.save(c);
                    }
                }
                return true;
            } else return false;

//            System.out.println("Отговор от Спиди: " + response.getStatusCode());
//            System.out.println(response.getBody().toString());
            // Ако статусът е 2xx, значи userName и password са приети.
//            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            System.err.println("Грешка при теста към Спиди: " + e.getMessage());
            return false;
        }
    }


    private Map<String, Object> createBaseBody(String user, String pass) {
        Map<String, Object> body = new HashMap<>();
        body.put("userName", user);
        body.put("password", pass);
        body.put("language", "BG");
        return body;
    }

    private Map<String, Object> postToSpeedy(String endpoint, Map<String, Object> body) {
        return restClient.post()
                .uri("https://api.speedy.bg/v1/" + endpoint)
//                .uri("https://demo.speedy.bg/v1/" + endpoint)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON) // Задължително за Спиди
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
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
}
