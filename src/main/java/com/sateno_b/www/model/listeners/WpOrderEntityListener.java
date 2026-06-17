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
        WpOrderEntity old = wpOrderEntity.getSnapshot();

        // Проверка за статус CANCELLED / REFUSED_AFTER_REVIEW / FAILED
        if (old != null && old.getStatus() != wpOrderEntity.getStatus()
                && (wpOrderEntity.getStatus() == OrderStatus.CANCELLED
                || wpOrderEntity.getStatus() == OrderStatus.REFUSED_AFTER_REVIEW
                || wpOrderEntity.getStatus() == OrderStatus.FAILED)) {

            boolean manual = wpOrderEntity.isManualCancellation();
            wpProductServiceProvider.ifAvailable(service -> service.restoreQuantity(wpOrderEntity, manual));
        }
        else if(old != null && old.getStatus() == OrderStatus.CANCELLED && wpOrderEntity.getStatus() != null
                && wpOrderEntity.getStatus() == OrderStatus.PROCESSING) {
            wpProductServiceProvider.ifAvailable(service -> service.getFromQuantity(wpOrderEntity));
        }

//        // Изпращаме WebSocket нотификация
        notificationServiceProvider.ifAvailable(service ->
                service.sendUpdate("orders", WsAction.UPDATED)
        );
    }
}
