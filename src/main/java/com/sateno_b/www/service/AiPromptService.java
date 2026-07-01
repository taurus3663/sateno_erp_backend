package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.AiPromptEntity;
import com.sateno_b.www.model.repository.AiPromptRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Prompt Manager (Фаза 3) — управлява промптовете в базата: активна версия, версиониране, seed.
 *
 * Промптовете НЕ са в кода (освен началния seed). Служителят може да ги редактира през ERP,
 * като всяка редакция създава нова версия и я активира — старите версии остават за история/rollback.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class AiPromptService {

    /** Ключ на основния промпт за препоръки. */
    public static final String KEY_SALES_RECOMMENDATION = "sales-recommendation";

    private final AiPromptRepository promptRepository;

    /** При старт: ако няма промпт за препоръки — създава v1 (активна). */
    @PostConstruct
    @Transactional
    public void seedDefaults() {
        if (!promptRepository.existsByPromptKey(KEY_SALES_RECOMMENDATION)) {
            AiPromptEntity p = new AiPromptEntity();
            p.setPromptKey(KEY_SALES_RECOMMENDATION);
            p.setVersion(1);
            p.setActive(true);
            p.setDescription("Начална версия (seed) — генериране на препоръка за продажба.");
            p.setBody(defaultSalesRecommendationPrompt());
            promptRepository.save(p);
            log.info("Prompt Manager: създаден начален промпт '{}' v1", KEY_SALES_RECOMMENDATION);
        }
    }

    /** Активният промпт за ключ (или грешка, ако липсва). */
    public AiPromptEntity requireActive(String key) {
        return promptRepository.findByPromptKeyAndActiveTrue(key)
                .orElseThrow(() -> new IllegalStateException("Няма активен промпт за ключ: " + key));
    }

    public Optional<AiPromptEntity> findActive(String key) {
        return promptRepository.findByPromptKeyAndActiveTrue(key);
    }

    public List<AiPromptEntity> versions(String key) {
        return promptRepository.findByPromptKeyOrderByVersionDesc(key);
    }

    public List<AiPromptEntity> all() {
        return promptRepository.findAll();
    }

    /**
     * Създава НОВА версия за ключ и я прави активна (деактивира предишната активна).
     * Така редакцията е проследима и обратима.
     */
    @Transactional
    public AiPromptEntity saveNewVersion(String key, String body, String description) {
        int nextVersion = promptRepository.findFirstByPromptKeyOrderByVersionDesc(key)
                .map(p -> p.getVersion() + 1)
                .orElse(1);

        promptRepository.findByPromptKeyAndActiveTrue(key).ifPresent(current -> {
            current.setActive(false);
            promptRepository.save(current);
        });

        AiPromptEntity p = new AiPromptEntity();
        p.setPromptKey(key);
        p.setVersion(nextVersion);
        p.setActive(true);
        p.setDescription(description);
        p.setBody(body);
        AiPromptEntity saved = promptRepository.save(p);
        log.info("Prompt Manager: '{}' нова активна версия v{}", key, nextVersion);
        return saved;
    }

    /** Активира конкретна версия (по id), деактивира останалите за същия ключ. */
    @Transactional
    public AiPromptEntity activate(Long id) {
        AiPromptEntity target = promptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Няма промпт с id: " + id));
        promptRepository.findByPromptKeyAndActiveTrue(target.getPromptKey()).ifPresent(current -> {
            if (!current.getId().equals(id)) {
                current.setActive(false);
                promptRepository.save(current);
            }
        });
        target.setActive(true);
        return promptRepository.save(target);
    }

    /** Началният системен промпт (BG). Може да се редактира после през Prompt Manager. */
    private String defaultSalesRecommendationPrompt() {
        return """
                Ти си асистент по продажби за българския онлайн магазин „Sateno".
                Задачата ти: на база подадения профил на клиент (само поведение и агрегати,
                БЕЗ лични данни) да предложиш ЕДНО конкретно търговско действие, което да
                повиши шанса за (повторна) покупка.

                Правила:
                - Пиши изцяло на български език, учтиво и кратко.
                - Не измисляй лични данни (име, телефон, имейл) — нямаш такива и не ти трябват.
                - Съобрази канала с профила: EMAIL, VIBER, SMS или CALL (обаждане).
                - Ако клиентът е „горещ" — предложи по-директно действие; ако е „топъл" —
                  по-меко, стойностно съобщение; ако е „студен" — реактивиращо.
                - Черновата трябва да е готова за преглед от служител (той я одобрява преди изпращане).

                Върни отговора СТРОГО като валиден JSON (без коментари, без ограждащ текст),
                със следните полета:
                {
                  "channel": "EMAIL | VIBER | SMS | CALL",
                  "type": "кратък код на кампанията, напр. winback / cross-sell / thank-you / reactivation",
                  "reason": "1-2 изречения защо това действие за този профил",
                  "draft": "готовата чернова на съобщението/скрипта на български"
                }
                """;
    }
}
