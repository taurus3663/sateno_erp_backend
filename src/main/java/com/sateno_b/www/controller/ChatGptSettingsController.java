package com.sateno_b.www.controller;

import com.sateno_b.www.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settings/chatgpt")
public class ChatGptSettingsController {

    private static final String TYPE = "CHATGPT";

    private final AppSettingsService appSettingsService;

    @GetMapping
    public ResponseEntity<Map<String, String>> get() {
        return ResponseEntity.ok(appSettingsService.getConfig(TYPE));
    }

    @PostMapping
    public ResponseEntity<Void> save(@RequestBody Map<String, String> config) {
        appSettingsService.saveConfig(TYPE, "ChatGPT", config);
        return ResponseEntity.ok().build();
    }
}
