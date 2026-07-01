package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.enums.QueueStatus;
import com.sateno_b.www.model.enums.RecommendationChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Запис в опашката за комуникация (AI Sales Assistant, Фаза 4).
 *
 * Създава се, когато служител ОДОБРИ AI препоръка. Пази готовото за изпращане
 * съобщение + канала. Във Фаза 4 записът само СТОИ в опашката ({@code QUEUED}) —
 * реалното изпращане (Provider Layer) е Фаза 6.
 *
 * Отделен е от {@link AiRecommendationEntity}, за да разделим „решението" (препоръка +
 * одобрение) от „доставката" (опити, статус на изпращане, грешки, retry).
 *
 * Добавяща таблица (нова) — старият код не я ползва, rollback е безопасен.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "communication_queue",
        indexes = {
                @Index(name = "idx_comm_queue_status", columnList = "status"),
                @Index(name = "idx_comm_queue_recommendation", columnList = "recommendation_id")
        }
)
public class CommunicationQueueEntity extends BaseEntity {

    /** Коя препоръка е породила записа. */
    @Column(name = "recommendation_id")
    private Long recommendationId;

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel")
    private RecommendationChannel channel;

    /** Готовото за изпращане съдържание (одобрената/редактирана чернова). */
    @Column(name = "payload_text", columnDefinition = "text")
    private String payloadText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private QueueStatus status = QueueStatus.QUEUED;

    /** Брой опити за изпращане (Фаза 6). */
    @Column(name = "attempts", columnDefinition = "integer default 0")
    private int attempts = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** Последна грешка при изпращане (Фаза 6). */
    @Column(name = "error", columnDefinition = "text")
    private String error;
}
