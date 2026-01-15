package com.sateno_b.www.init;

import com.sateno_b.www.model.entity.UserEntity;
import com.sateno_b.www.model.repository.UserRepository;
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

    @Override
    public void run(String... args) throws Exception {
        initAdmin();
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
