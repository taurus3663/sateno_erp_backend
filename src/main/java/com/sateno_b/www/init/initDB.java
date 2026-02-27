package com.sateno_b.www.init;

import com.sateno_b.www.model.entity.UserEntity;
import com.sateno_b.www.model.repository.UserRepository;
import com.sateno_b.www.service.NekorektenService;
import lombok.RequiredArgsConstructor;
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

    @Override
    public void run(String... args) throws Exception {
        initAdmin();

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
}
