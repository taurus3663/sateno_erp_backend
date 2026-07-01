package com.sateno_b.www.model.enums;

/**
 * Жизнен цикъл на AI препоръка (AI Sales Assistant).
 *
 * Фаза 3 създава чернови в {@link #PENDING_APPROVAL}.
 * Останалите статуси се задействат от Approval Workflow + Communication Queue (Фаза 4):
 * PENDING_APPROVAL → APPROVED → QUEUED → SENT / FAILED, или → REJECTED.
 *
 * Правило: НИЩО не се изпраща без одобрение.
 */
public enum RecommendationStatus {
    PENDING_APPROVAL,
    APPROVED,
    QUEUED,
    SENT,
    FAILED,
    REJECTED
}
