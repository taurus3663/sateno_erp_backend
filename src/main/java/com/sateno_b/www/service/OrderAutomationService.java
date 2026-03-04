package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.EmailSendRequest;
import com.sateno_b.www.model.entity.EmailLogEntity;
import com.sateno_b.www.model.entity.OrderAutomationTaskEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpOrderEntity;
import com.sateno_b.www.model.enums.OrderStatus;
import com.sateno_b.www.model.enums.TaskType;
import com.sateno_b.www.model.repository.OrderAutomationTaskRepository;
import com.sateno_b.www.model.repository.WpOrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderAutomationService {

    private final OrderAutomationTaskRepository orderAutomationTaskRepository;
    private final EmailService emailService;
    private final WpOrderRepository wpOrderRepository;

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void processAutomation() {
        // Използваме Instant.now(), което е супер за UTC съпоставка
        List<OrderAutomationTaskEntity> tasks = orderAutomationTaskRepository.findAllByProcessedFalseAndScheduledTimeBefore(Instant.now());

        for (OrderAutomationTaskEntity task : tasks) {
            log.info("Processing automation task {} for order {}", task.getTaskType(), task.getOrder().getId());
            WpOrderEntity order = task.getOrder();
            SiteEntity site = order.getSite();

            boolean confirmed = isOrderConfirmed(order);

            // 1. Ако е потвърдена, просто маркираме задачата като обработена
            if (confirmed || order.getStatus() != OrderStatus.PROCESSING) {
                task.setProcessed(true);
                orderAutomationTaskRepository.save(task);
                orderAutomationTaskRepository.deleteAllByOrderAndProcessedFalse(order);
                continue;
            }

            // 2. Логика по типове
            switch (task.getTaskType()) {
                case SECOND_EMAIL -> {
                    if (!confirmed && site.getSecondOrderMessage() != null) {
                        executeEmail(order, site.getSecondOrderMessage());

                        // Планираме следващия, само ако таймерът му е валиден
                        if (site.getThirdOrderMessageTimer() != null && site.getThirdOrderMessageTimer() > 0) {
                            scheduleTask(order, TaskType.THIRD_EMAIL, site.getThirdOrderMessageTimer());
                        }
                    }
                }
                case THIRD_EMAIL -> {
                    if (!confirmed && site.getThirdOrderMessage() != null) {
                        executeEmail(order, site.getThirdOrderMessage());
                    }
                }
                case STATUS_CHANGE -> {
                    // Тук можеш да добавиш проверка дали поръчката вече не е в друг краен статус
                    if (!confirmed) {
                        order.setStatus(OrderStatus.WAITING);
                        wpOrderRepository.save(order);
                    }
                }
            }

            task.setProcessed(true);
            orderAutomationTaskRepository.save(task);
        }
    }

    public void scheduleTask(WpOrderEntity order, TaskType type, Long delayMinutes) {
        if (delayMinutes == null || delayMinutes <= 0) return;

        if (isOrderConfirmed(order)) {
            return;
        }

        // Внимавай: Името на класа трябва да е OrderAutomationTaskEntity, както е инжектирано горе
        OrderAutomationTaskEntity task = new OrderAutomationTaskEntity();
        task.setOrder(order);
        task.setTaskType(type);
        // Instant.plus(delay, ChronoUnit.MINUTES) е по-чистият начин
        task.setScheduledTime(Instant.now().plus(delayMinutes, java.time.temporal.ChronoUnit.MINUTES));
        task.setProcessed(false);

        orderAutomationTaskRepository.save(task);
    }

    private boolean isOrderConfirmed(WpOrderEntity order) {
        // Проверка в лога на имейлите за флаг confirmed
        return order.getEmails() != null &&
                order.getEmails().stream().anyMatch(EmailLogEntity::isConfirmed);
    }

    private void executeEmail(WpOrderEntity order, String content) {
        if (content == null || content.isBlank()) return;

        EmailSendRequest request = new EmailSendRequest();
        request.setTo(order.getCustomer().getEmail()); // провери дали е customerEmail или customer.getEmail()
        request.setSubject("Напомняне за вашата поръчка");
        request.setContent(content);
        request.setConfigId(order.getSite().getEmail().getId());
        request.setGenConfirm(true); // Важно за следващите стъпки

        EmailLogEntity emailLogEntity = emailService.sendEmail(request);
        order.getEmails().add(emailLogEntity);
        wpOrderRepository.save(order);
    }
}
