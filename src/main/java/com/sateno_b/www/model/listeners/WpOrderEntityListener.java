package com.sateno_b.www.model.listeners;

import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.enums.WsAction;
import com.sateno_b.www.service.NotificationService;
import com.sateno_b.www.service.WpProductService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
public class WpOrderEntityListener {


    private final ObjectProvider<NotificationService> notificationServiceProvider;
    private final ObjectProvider<WpProductService> wpProductServiceProvider;

    public WpOrderEntityListener(
            ObjectProvider<NotificationService> notificationServiceProvider,
            ObjectProvider<WpProductService> wpProductServiceProvider) {
        this.notificationServiceProvider = notificationServiceProvider;
        this.wpProductServiceProvider = wpProductServiceProvider;
    }

    @PostUpdate
    public void onOrderChange(WpOrderEntity wpOrderEntity) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("11");
        WpOrderEntity old = wpOrderEntity.getSnapshot();

        // Проверка за статус CANCELLED
        if (old != null && old.getStatus() != wpOrderEntity.getStatus()
                && (wpOrderEntity.getStatus() == OrderStatus.CANCELLED ||
                wpOrderEntity.getStatus() == OrderStatus.FAILED) ) {

            // Извикваме сервиза само при нужда през Provider-а
            wpProductServiceProvider.ifAvailable(service -> service.restoreQuantity(wpOrderEntity));
        }

//        // Изпращаме WebSocket нотификация
        notificationServiceProvider.ifAvailable(service ->
                service.sendUpdate("orders", WsAction.UPDATED)
        );
    }
}
