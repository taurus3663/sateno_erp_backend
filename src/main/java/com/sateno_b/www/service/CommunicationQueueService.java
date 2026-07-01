package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.AiRecommendationEntity;
import com.sateno_b.www.model.entity.CommunicationQueueEntity;
import com.sateno_b.www.model.enums.QueueStatus;
import com.sateno_b.www.model.repository.CommunicationQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Communication Queue (AI Sales Assistant, Фаза 4).
 *
 * Одобрена препоръка → запис в опашката ({@code QUEUED}). Във Фаза 4 записът само стои —
 * НИЩО не се изпраща (изискване от спецификацията: Phase 1 спира на Queue запис).
 *
 * Реалното изпращане (Provider Layer: Email/SMS/WhatsApp + Viber) е Фаза 6 и се пуска чрез
 * флага {@code communication.sending.enabled=true}. Докато е false, процесорът само отчита.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class CommunicationQueueService {

    private final CommunicationQueueRepository queueRepository;

    /** Ключ за включване на реалното изпращане (Фаза 6). По подразбиране ИЗКЛЮЧЕНО. */
    @Value("${communication.sending.enabled:false}")
    private boolean sendingEnabled;

    /** Добавя одобрена препоръка в опашката (идемпотентно за препоръка). */
    @Transactional
    public CommunicationQueueEntity enqueue(AiRecommendationEntity rec, String payloadText) {
        if (queueRepository.existsByRecommendationId(rec.getId())) {
            log.info("Queue: препоръка {} вече е в опашката — пропускам.", rec.getId());
            return null;
        }
        CommunicationQueueEntity q = new CommunicationQueueEntity();
        q.setRecommendationId(rec.getId());
        q.setCustomerId(rec.getCustomerId());
        q.setChannel(rec.getChannel());
        q.setPayloadText(payloadText);
        q.setStatus(QueueStatus.QUEUED);
        CommunicationQueueEntity saved = queueRepository.save(q);
        log.info("Queue: добавен запис {} (препоръка {}, канал {}) в състояние QUEUED",
                saved.getId(), rec.getId(), rec.getChannel());
        return saved;
    }

    public List<CommunicationQueueEntity> queued() {
        return queueRepository.findByStatusOrderByCreateTimeAsc(QueueStatus.QUEUED);
    }

    public List<CommunicationQueueEntity> recent() {
        return queueRepository.findTop100ByOrderByCreateTimeDesc();
    }

    public long countQueued() {
        return queueRepository.countByStatus(QueueStatus.QUEUED);
    }

    public boolean isSendingEnabled() {
        return sendingEnabled;
    }

    /**
     * Периодичен процесор на опашката. Във Фаза 4 НЕ изпраща нищо — само отчита броя чакащи,
     * ако изпращането е изключено. Реалната логика (Provider Layer) се добавя във Фаза 6.
     */
    @Scheduled(fixedDelayString = "${communication.queue.poll-ms:300000}")
    public void processQueue() {
        long pending = countQueued();
        if (pending == 0) return;
        if (!sendingEnabled) {
            log.info("Queue: {} чакащи записа, но изпращането е ИЗКЛЮЧЕНО (Фаза 4 — само записи).", pending);
            return;
        }
        // TODO(Фаза 6): взимане на QUEUED записи и изпращане през Provider Layer
        // (Email/SMS/WhatsApp съществуват; Viber се добавя). Тук само маркер.
        log.warn("Queue: изпращането е включено, но Provider Layer още не е внедрен (Фаза 6).");
    }
}
