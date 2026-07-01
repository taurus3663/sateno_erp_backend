package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.enums.RecommendationChannel;
import com.sateno_b.www.model.enums.RecommendationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * AI препоръка за търговско действие (AI Sales Assistant, Фаза 3).
 *
 * Създава се като ЧЕРНОВА ({@code PENDING_APPROVAL}) от {@code AiRecommendationService}.
 * Одобрение/изпращане идват във Фаза 4 (Approval Workflow + Communication Queue).
 *
 * Добавяща таблица (нова) — старият код не я ползва, rollback е безопасен.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "ai_recommendation",
        indexes = {
                @Index(name = "idx_ai_reco_customer", columnList = "customer_id"),
                @Index(name = "idx_ai_reco_status", columnList = "status")
        }
)
public class AiRecommendationEntity extends BaseEntity {

    @Column(name = "customer_id")
    private Long customerId;

    /** Снимка на Lead Score/tier към момента на генериране (за проследимост). */
    @Column(name = "lead_score")
    private Integer leadScore;

    @Column(name = "lead_tier")
    private String leadTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel")
    private RecommendationChannel channel;

    /** Тип кампания (свободен код от AI: winback / cross-sell / reactivation ...). */
    @Column(name = "rec_type")
    private String recType;

    /** Готовата чернова за преглед (съобщение/скрипт), на български. */
    @Column(name = "ai_draft_text", columnDefinition = "text")
    private String aiDraftText;

    /** Обосновка от AI защо това действие за този профил. */
    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RecommendationStatus status = RecommendationStatus.PENDING_APPROVAL;

    /** Кой е създал: тук винаги AI + доставчик (напр. "AI:claude"). */
    @Column(name = "created_by")
    private String createdBy;

    /** Кой служител е одобрил (Фаза 4). */
    @Column(name = "approved_by")
    private String approvedBy;

    /** Кога е взето решение (одобрено/отхвърлено) — Фаза 4. */
    @Column(name = "decided_at")
    private Instant decidedAt;

    // --- проследимост към промпта, който е генерирал препоръката ---
    @Column(name = "prompt_key")
    private String promptKey;

    @Column(name = "prompt_version")
    private Integer promptVersion;

    /** Кой AI доставчик е генерирал (claude/openai). */
    @Column(name = "ai_provider")
    private String aiProvider;
}
