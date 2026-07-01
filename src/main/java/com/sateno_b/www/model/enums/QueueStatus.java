package com.sateno_b.www.model.enums;

/**
 * Състояние на запис в Communication Queue (AI Sales Assistant, Фаза 4).
 *
 * Фаза 4 създава записи в {@link #QUEUED} и СПИРА дотук — без реално изпращане.
 * Реалното изпълнение (SENDING → SENT/FAILED) идва във Фаза 6 (Provider Layer:
 * Email/SMS/WhatsApp + Viber).
 */
public enum QueueStatus {
    QUEUED,
    SENDING,
    SENT,
    FAILED,
    CANCELLED
}
