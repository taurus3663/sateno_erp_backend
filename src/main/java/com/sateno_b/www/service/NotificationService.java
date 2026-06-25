package com.sateno_b.www.service;

import com.sateno_b.www.model.enums.WsAction;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WsAction> lastAction = new ConcurrentHashMap<>();

    private static final long DEBOUNCE_SECONDS = 5;

    public void sendUpdate(String topic, WsAction action) {
        lastAction.put(topic, action);

        ScheduledFuture<?> existing = pending.remove(topic);
        if (existing != null) existing.cancel(false);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            WsAction finalAction = lastAction.remove(topic);
            pending.remove(topic);
            if (finalAction == null) return;

            Map<String, String> message = Map.of(
                    "topic", topic,
                    "action", finalAction.name(),
                    "timestamp", String.valueOf(System.currentTimeMillis())
            );
            simpMessagingTemplate.convertAndSend("/topic/" + topic, message);
        }, DEBOUNCE_SECONDS, TimeUnit.SECONDS);

        pending.put(topic, future);
    }
}
