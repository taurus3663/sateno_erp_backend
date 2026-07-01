package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.AiRecommendationEntity;
import com.sateno_b.www.model.entity.CommunicationQueueEntity;
import com.sateno_b.www.model.enums.RecommendationChannel;
import com.sateno_b.www.model.enums.RecommendationStatus;
import com.sateno_b.www.model.repository.AiRecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Approval Workflow (AI Sales Assistant, Фаза 4).
 *
 * Всяка AI препоръка е ЧЕРНОВА ({@code PENDING_APPROVAL}). Служителят може да я:
 *   - редактира (текст/канал), докато чака,
 *   - ОДОБРИ → минава в опашката за комуникация (нищо не се изпраща — Фаза 4),
 *   - ОТХВЪРЛИ → приключва без действие.
 *
 * Роли НЯМА (решение §7.4): всеки с достъп до ERP може да одобрява. {@code decidedBy} е
 * само за проследимост (кой е решил). НИЩО не се изпраща без одобрение.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class RecommendationApprovalService {

    private final AiRecommendationRepository recommendationRepository;
    private final CommunicationQueueService queueService;

    /** Редакция на чернова, докато чака одобрение (текст и/или канал). */
    @Transactional
    public AiRecommendationEntity edit(Long id, String newText, RecommendationChannel newChannel) {
        AiRecommendationEntity rec = require(id);
        ensurePending(rec);
        if (newText != null) rec.setAiDraftText(newText);
        if (newChannel != null) rec.setChannel(newChannel);
        return recommendationRepository.save(rec);
    }

    /**
     * Одобрява чернова: маркира APPROVED и я добавя в Communication Queue (QUEUED).
     *
     * @param editedText по избор — редактиран текст при одобрението (иначе се ползва черновата)
     * @param editedChannel по избор — сменен канал при одобрението
     * @param decidedBy кой одобрява (за проследимост; може да е null)
     */
    @Transactional
    public AiRecommendationEntity approve(Long id, String editedText,
                                          RecommendationChannel editedChannel, String decidedBy) {
        AiRecommendationEntity rec = require(id);
        ensurePending(rec);

        if (editedText != null) rec.setAiDraftText(editedText);
        if (editedChannel != null) rec.setChannel(editedChannel);

        rec.setStatus(RecommendationStatus.APPROVED);
        rec.setApprovedBy(decidedBy);
        rec.setDecidedAt(Instant.now());
        AiRecommendationEntity saved = recommendationRepository.save(rec);

        CommunicationQueueEntity q = queueService.enqueue(saved, saved.getAiDraftText());
        log.info("Approval: препоръка {} ОДОБРЕНА от '{}' → queue запис {}",
                id, decidedBy, q != null ? q.getId() : "(вече в опашка)");
        return saved;
    }

    /** Отхвърля чернова: маркира REJECTED, нищо не влиза в опашката. */
    @Transactional
    public AiRecommendationEntity reject(Long id, String reason, String decidedBy) {
        AiRecommendationEntity rec = require(id);
        ensurePending(rec);
        rec.setStatus(RecommendationStatus.REJECTED);
        rec.setApprovedBy(decidedBy);
        rec.setDecidedAt(Instant.now());
        if (reason != null && !reason.isBlank()) {
            String note = "\n\n[Отхвърлено: " + reason + "]";
            rec.setReason((rec.getReason() == null ? "" : rec.getReason()) + note);
        }
        AiRecommendationEntity saved = recommendationRepository.save(rec);
        log.info("Approval: препоръка {} ОТХВЪРЛЕНА от '{}'", id, decidedBy);
        return saved;
    }

    private AiRecommendationEntity require(Long id) {
        return recommendationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Няма препоръка с id: " + id));
    }

    /** Позволяваме решение само върху чернова (валиден преход). */
    private void ensurePending(AiRecommendationEntity rec) {
        if (rec.getStatus() != RecommendationStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Препоръка " + rec.getId() + " не е в състояние за решение (текущо: "
                            + rec.getStatus() + ").");
        }
    }
}
