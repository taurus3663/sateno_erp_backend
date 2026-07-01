package com.sateno_b.www.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Промпт за AI Sales Assistant (Prompt Manager, Фаза 3).
 *
 * Промптовете се пазят в БАЗАТА, не в кода — за да се редактират и версионират без деплой
 * (изискване от плана §3.2). Един ключ ({@code promptKey}) има много версии; точно една е
 * активна в даден момент.
 *
 * Добавяща таблица (нова) — старият код не я ползва, rollback е безопасен.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "ai_prompt",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ai_prompt_key_version",
                columnNames = {"prompt_key", "version"}
        ),
        indexes = {
                @Index(name = "idx_ai_prompt_key_active", columnList = "prompt_key, active")
        }
)
public class AiPromptEntity extends BaseEntity {

    /** Логически ключ, напр. "sales-recommendation". */
    @Column(name = "prompt_key", nullable = false)
    private String promptKey;

    /** Версия на промпта под този ключ (1, 2, 3...). */
    @Column(name = "version", nullable = false)
    private int version;

    /** Само една активна версия на ключ. */
    @Column(name = "active", columnDefinition = "boolean default false")
    private boolean active;

    /** Кратко описание за човек (за какво служи / какво е сменено). */
    @Column(name = "description")
    private String description;

    /** Тялото на системния промпт. */
    @Column(name = "body", columnDefinition = "text")
    private String body;
}
