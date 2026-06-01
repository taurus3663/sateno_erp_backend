package com.sateno_b.www.init;

import com.sateno_b.www.model.dto.EmailSendRequest;
import com.sateno_b.www.model.entity.CustomerEntity;
import com.sateno_b.www.model.entity.DiscountPhone;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.UserEntity;
import com.sateno_b.www.model.repository.*;
import com.sateno_b.www.service.*;
import com.sateno_b.www.shared.Shared;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class initDB implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NekorektenService nekorektenService;
    private final SiteRepository siteRepository;
    private final CurrencyService currencyService;
    private final SpeedyService speedyService;
    private final WhatsAppService whatsAppService;
    private final WpOrderRepository wpOrderRepository;
    private final CustomerRepository customerRepository;
    private final DiscountPhoneRepository discountPhoneRepository;
    private final MetaAdsService metaAdsService;
    private final GoogleAdsService googleAdsService;

    @Override
    public void run(String... args) throws Exception {
        initAdmin();
//        sendEmailTest();
//        callAi();
//        nekorektenService.checkPhone("0888182076");
//        {"message":null,"items":
//        [{"id":"6PpryA2Zxz","firstName":"Не**","lastName":"Желяз****","email":"nelit****@gmail.com",
//        "phone":"+359888182076","text":"Поръча си от детските книжки, които предлагаме.
//        Позвънихме й за потвърждение по телефона и й ги изпратихме. Дни наред не благоволи
//        да отиде до куриерите да си вземе пратката. Звъняхме й отново, за да я подсетим. И
//        въпреки това, пратката се върна към нас без да бъде потърсена.\nНЕКОРЕКТНА!!!",
//        "facebookUrl":null,"siteUrl":null,"siteUrlMapped":null,"user":{"id":"V4pjrQJp0X",
//        "firstName":"Angel","lastName":"Matsanov","email":"angelmatsa*****@gmail.com"},
//        "city":null,"parent":null,"children":[],"categories":[],"files":[],"createDate":
//        "2025-01-11T21:22:21+00:00","views":16,"liked":0,"visibleOnlyForCreator":false,
//        "isArchived":false}],"count":1,"currentPage":1,"itemsPerPage":20,"server":
//        {"date":"2026-02-27 20:22:33","httpCode":200}}

//        initCurrencyTest();
//        Map<String, Object> body = speedyService.createBaseBody("1908628", "SpidiAdmirali2558$");
//        Map<String, Object> parcel = new HashMap<>();
//        parcel.put("id", "63538814858"); // Номерът трябва да е в id
//        body.put("parcels", List.of(parcel));
//        var stringObjectMap = speedyService.postToSpeedy("track", body);
//        System.out.println(stringObjectMap);


//        String msg = "Здравейте! Поръчка # е приета. Благодарим Ви!";
//        String response = whatsAppService.sendWhatsApp("0894396766", msg);
//        System.out.println(response);

//        fixCustomerNumbers();
//        populateDiscountPN();

//        try{
////                    String response = whatsAppService.sendWhatsAppTemplate("0894396766", "12345");
////        System.out.println(response);
////            whatsAppService.checkMessageStatus("e6ec4653-b2a3-45ba-be07-9c935e5446cc");
////            whatsAppService.checkStatus("acddc425-6bdb-4fc9-98ec-e9bf89d1d706");
//        } catch(Exception e){
//            e.printStackTrace();



        try {
//            meta();
//            googleApi();
        } catch (Exception e) {
            System.err.println("Meta API инициализацията се провали: " + e.getMessage());
            // НЕ хвърляй RuntimeException тук, за да продължи стартът на приложението!
        }
//        googleApi();

    }

// response обикновено съдържа ID на съобщението или "OK"
    private void meta() {
//        String accessToken = "EAAOeqLFcsikBRonwIxB1GsCL0QCMgiPY7hWsOwkdPE5cYc2xfEuZBrIZB3z8I1Tfe1ALxocUgGUj9jWqb2ldagab4lM8wdZCcpKmDL3FYWutmaFy5KwwlHFKVKJB89tDP7i9dWHKbAj1w6M2ZCDXFkzPG8BR65Avm2g79mQYImEAuJWfD5jOiTMtRuigogEigbQcy0RhmbO7jvDvDrmYjM2h2ZAt3ZBmbodKTR";
//        String accessToken = "EAANZAKuM0VOoBRtdwe5z7eRTtT49voey2jEWopUd4qnDOF4iwNpGcuX6Sdje62p5rPIVnNc21jAGUktYYhWyjyuI69HwAK2xc3h5wqlv23Ch1IZAntFRPlZCLzejoTdT3RTsQ3B7YxGD9KbdcLRBZCCcbjfJDRYaPCS0UrCmAwC5t4v0ZBsA0Hwz4jP3vXRus7QZDZD";
//        String adAccountId = "act_636762158736104";
//        Map<String, Object> myAdAccounts = metaAdsService.getMyAdAccounts(accessToken);
//        System.out.println(myAdAccounts);
//        Map<String, Object> body = metaAdsService.getCampaignInsights(adAccountId, accessToken );
//        System.out.println(body);
    }

    private void initAdmin() {
        List<UserEntity> adminDB1 = userRepository.findAll();

        if(adminDB1.isEmpty()) {
            UserEntity admin = new UserEntity();
            admin.setUsername("root");
            admin.setPassword(passwordEncoder.encode("root"));
            admin.setEmail("taurus.ali47@gmail.com");
            userRepository.save(admin);
        }
    }

    private final EmailService emailService;

    private void sendEmailTest() {
        String buttonHtml = """
        <div style="text-align: center; margin: 20px 0;">
            <a href="https://your-erp-link.com/confirm?id=123" 
               style="background-color: #3B82F6; 
                      color: white; 
                      padding: 12px 25px; 
                      text-decoration: none; 
                      border-radius: 5px; 
                      font-weight: bold; 
                      display: inline-block;
                      font-family: Arial, sans-serif;">
                ПОТВЪРДИ ПОРЪЧКАТА
            </a>
        </div>
    """;
        String fullHtmlBody = "<h1>Здравейте!</h1>" +
                "<p>Моля, натиснете бутона отдолу, за да потвърдите вашата поръчка:</p>" +
                buttonHtml +
                "<p>Благодарим ви!</p>";


        EmailSendRequest emailSendRequest = new EmailSendRequest();
        emailSendRequest.setConfigId(2L);
        emailSendRequest.setTo("taurus.ali47@gmail.com");
//        emailSendRequest.setTo("silyan.silyanov@gmail.com");
        emailSendRequest.setSubject("TEST");
        emailSendRequest.setContent(fullHtmlBody);
//        emailService.sendEmail(emailSendRequest);
    }

    private final ChatGptService chatGptService;

    private void callAi() {
//        System.out.println("TEST11");
        String tr = chatGptService.translateText("КАКВО ПРАВИШ?", "преведи го на английски");
//        System.out.println(tr);
    }

    private void initCurrencyTest() {
//        BigDecimal convert = currencyService.convert(BigDecimal.valueOf(5.10), "EUR", "RON");
//        System.out.println(convert);
//        BigDecimal convert1 = currencyService.convert(BigDecimal.valueOf(52), "EUR", "RON");
//        BigDecimal convert2 = currencyService.convert(BigDecimal.valueOf(54), "EUR", "RON");
//        BigDecimal convert3 = currencyService.convert(BigDecimal.valueOf(55), "EUR", "RON");
//        BigDecimal convert4 = currencyService.convert(BigDecimal.valueOf(56), "EUR", "RON");
//        BigDecimal convert5 = currencyService.convert(BigDecimal.valueOf(57), "EUR", "RON");
//        BigDecimal convert6 = currencyService.convert(BigDecimal.valueOf(58), "EUR", "RON");
//        BigDecimal convert7 = currencyService.convert(BigDecimal.valueOf(59), "EUR", "RON");
//        System.out.println(convert);
//        System.out.println(convert1);
//        System.out.println(convert2);
//        System.out.println(convert3);
//        System.out.println(convert4);
//        System.out.println(convert5);
//        System.out.println(convert6);
//        System.out.println(convert7);
    }

    private void populateDiscountPN() {
        // 1. Взимаме всички записи за телефонни отстъпки
        List<DiscountPhone> allDiscountPhones = discountPhoneRepository.findAll();

        // 2. Групираме ги по телефонен номер, за да открием дубликатите
        Map<String, List<DiscountPhone>> phonesGrouped = allDiscountPhones.stream()
                .filter(p -> p.getPhoneNumber() != null)
                .collect(Collectors.groupingBy(p -> Shared.fixBGNumber(p.getPhoneNumber())));

        // 3. Обхождаме всяка група от телефони
        for (Map.Entry<String, List<DiscountPhone>> entry : phonesGrouped.entrySet()) {
            String phoneNumber = entry.getKey();
            List<DiscountPhone> phonesList = entry.getValue();

            // Ако има повече от 1 запис за този номер, значи имаме дубликати
            if (phonesList.size() > 1) {
                // Сортираме списъка така, че НАЙ-НОВИЯТ запис да бъде на индекс 0
                // За целта сравняваме по ID (ако е генерирано последователно) или по дата (напр. getCreatedAt())
                phonesList.sort((p1, p2) -> p2.getId().compareTo(p1.getId()));
                // ^ Ако имаш дата, използвай: p2.getCreatedAt().compareTo(p1.getCreatedAt())

                // Най-новият остава
                DiscountPhone newestPhone = phonesList.get(0);

                // Всички останали (от индекс 1 нататък) са по-стари и трябва да бъдат изтрити
                List<DiscountPhone> olderDuplicates = phonesList.subList(1, phonesList.size());

                // Изтриваме старите дубликати от базата данни
                discountPhoneRepository.deleteAll(olderDuplicates);

                // Продължаваме работа само с най-новия запис
                processCustomerMapping(phoneNumber, newestPhone);
            } else {
                // Ако номерът е уникален, директно го прехвърляме към мапинга
                processCustomerMapping(phoneNumber, phonesList.get(0));
            }
        }
    }

    // Спомагателен метод, който прави реалното свързване с клиента
    private void processCustomerMapping(String phoneNumber, DiscountPhone phone) {
        Optional<CustomerEntity> byPhone = customerRepository.findByPhone(Shared.fixBGNumber(phoneNumber));
        if (byPhone.isPresent()) {
            phone.setCustomer(byPhone.get());
            discountPhoneRepository.save(phone);
        }
    }

    private void fixCustomerNumbers() {
        int pageSize = 100;
        int pageNumber = 0;
        Page<CustomerEntity> page;

        do {
            page = customerRepository.findAll(PageRequest.of(pageNumber, pageSize));
            List<CustomerEntity> customers = page.getContent();

            for (CustomerEntity customer : customers) {
                if (customer.getPhone() != null) {
                    customer.setPhone(Shared.fixBGNumber(customer.getPhone()));
                }
            }
            customerRepository.saveAll(customers);

            pageNumber++;
        } while (page.hasNext());
    }

    private void googleApi() {
        try {
            System.out.println("T2");
            List<String> campaignNames = googleAdsService.getCampaignNames(7532974920L);
            System.out.println("T");
//            System.out.println(campaignNames);
//            System.out.println(campaignNames.size());
//            googleAdsService.gen();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
