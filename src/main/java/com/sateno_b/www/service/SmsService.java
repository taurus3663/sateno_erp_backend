package com.sateno_b.www.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.smsapi.api.SmsFactory;

@Service
@RequiredArgsConstructor
public class SmsService {

//    private final SmsFactory smsFactory;
    private final String OAUTH_TOKEN = "";

    @PostConstruct
    public void init() {
        // Използваме OAuth2 токен за оторизация

    }
}
