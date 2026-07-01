package com.sateno_b.www.service.ai;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Избира кой AI доставчик да ползва (Фаза 3).
 *
 * Провайдърите се инжектират подредени по {@code @Order} (Claude=1 основен, OpenAI=2 резервен).
 * Router-ът връща първия наличен ({@link AiProvider#isAvailable()}). Ако основният падне при
 * реален повик, пробва следващия наличен — така един счупен доставчик не спира процеса.
 */
@Component
@Log4j2
public class AiProviderRouter {

    private final List<AiProvider> providers;

    public AiProviderRouter(List<AiProvider> providers) {
        this.providers = providers;
        log.info("AI провайдъри (по приоритет): {}",
                providers.stream().map(AiProvider::name).toList());
    }

    /** Първият наличен доставчик, ако има такъв. */
    public Optional<AiProvider> primary() {
        return providers.stream().filter(AiProvider::isAvailable).findFirst();
    }

    /** Всички доставчици (за статус/диагностика). */
    public List<AiProvider> all() {
        return providers;
    }

    /** Има ли поне един готов доставчик. */
    public boolean anyAvailable() {
        return providers.stream().anyMatch(AiProvider::isAvailable);
    }

    /**
     * Изпълнява повика през първия наличен доставчик; при грешка пробва следващите.
     * Връща резултат + името на доставчика, който е успял.
     *
     * @throws IllegalStateException ако няма наличен доставчик или всички паднат
     */
    public Result complete(String systemPrompt, String userPrompt) {
        List<AiProvider> available = providers.stream().filter(AiProvider::isAvailable).toList();
        if (available.isEmpty()) {
            throw new IllegalStateException("Няма конфигуриран AI доставчик (липсва API ключ).");
        }
        RuntimeException last = null;
        for (AiProvider p : available) {
            try {
                String text = p.complete(systemPrompt, userPrompt);
                return new Result(p.name(), text);
            } catch (RuntimeException e) {
                log.warn("AI доставчик '{}' се провали: {}. Пробвам следващия.", p.name(), e.getMessage());
                last = e;
            }
        }
        throw new IllegalStateException("Всички AI доставчици се провалиха.", last);
    }

    /** Резултат от AI повик: кой доставчик + текст. */
    public record Result(String provider, String text) {}
}
