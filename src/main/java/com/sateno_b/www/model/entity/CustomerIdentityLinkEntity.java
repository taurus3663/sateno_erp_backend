package com.sateno_b.www.model.entity;

import com.sateno_b.www.model.enums.IdentityMatchType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Одит на свързване „анонимен посетител (сесия) → реален клиент"
 * (слой за идентичност на AI Sales Assistant).
 *
 * Само-добавяща таблица: всяко свързване е ред; при грешно свързване се маркира
 * {@code active=false} (unlink) — историята се пази, нищо не се трие.
 *
 * Добавяща таблица (нова) — старият код не я ползва, rollback е безопасен.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "customer_identity_link",
        indexes = {
                @Index(name = "idx_identity_customer", columnList = "customer_id"),
                @Index(name = "idx_identity_session", columnList = "site_id, session_token")
        }
)
public class CustomerIdentityLinkEntity extends BaseEntity {

    @Column(name = "site_id")
    private Long siteId;

    /** Анонимният токен на посетителя (satl_sid). */
    @Column(name = "session_token")
    private String sessionToken;

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type")
    private IdentityMatchType matchType;

    /** 0–100. Автоматично свързваме само при 100 (телефон/имейл/поръчка). */
    @Column(name = "confidence", columnDefinition = "integer default 100")
    private int confidence = 100;

    /** Активна ли е връзката (false = ръчно раз-свързана / конфликт). */
    @Column(name = "active", columnDefinition = "boolean default true")
    private boolean active = true;

    /** Бележка (напр. маркер за конфликт при споделено устройство). */
    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "unlinked_at")
    private Instant unlinkedAt;

    @Column(name = "unlinked_by")
    private String unlinkedBy;
}
