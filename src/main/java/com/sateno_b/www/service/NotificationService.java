package com.sateno_b.www.service;

import com.sateno_b.www.model.enums.WsAction;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public void sendUpdate(String topic, WsAction action) {
        Map<String, String> message = Map.of(
                "topic", topic,
                "action", action.name(),
                "timestamp", String.valueOf(System.currentTimeMillis())
        );

        simpMessagingTemplate.convertAndSend("/topic/" + topic, message);
    }
}
