package com.sateno_b.www.init;

import com.sateno_b.www.model.dto.EmailSendRequest;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.UserEntity;
import com.sateno_b.www.model.repository.SiteRepository;
import com.sateno_b.www.model.repository.UserRepository;
import com.sateno_b.www.service.ChatGptService;
import com.sateno_b.www.service.EmailService;
import com.sateno_b.www.service.NekorektenService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class initDB implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NekorektenService nekorektenService;
    private final SiteRepository siteRepository;

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
        System.out.println(tr);
    }
}
