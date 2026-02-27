package com.sateno_b.www.model.listeners;

import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.enums.WsAction;
import com.sateno_b.www.service.NotificationService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WpOrderEntityListener {

    private final NotificationService notificationService;

    @PostUpdate
    @PostPersist
    @PostRemove
    public void onOrderChange(WpOrderEntity wpOrderEntity) {
//        NotificationService notificationService = Bean
        notificationService.sendUpdate("orders", WsAction.UPDATED);
    }
}
