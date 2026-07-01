package com.sateno_b.www.service.ai;

/**
 * Общ интерфейс за AI доставчик (Фаза 3 на AI Sales Assistant).
 *
 * Целта е ERP-то да НЕ е обвързано с конкретен доставчик (правило от AI Prompt Rules.md).
 * Днес: {@link ClaudeAiProvider} (основен) + {@link OpenAiProvider} (резервен).
 * Утре може да се добави още един — без промяна на бизнес логиката, която ползва интерфейса.
 *
 * ВАЖНО (GDPR, решение на Асан 01.07.2026): към който и да е доставчик се подава
 * САМО ID на клиента + поведение/агрегати. Име/телефон/имейл НЕ напускат ERP.
 */
public interface AiProvider {

    /** Кратко име за логове/трасиране (напр. "claude", "openai"). */
    String name();

    /**
     * Дали доставчикът е конфигуриран и готов за ползване
     * (напр. има валиден API ключ). Ако не е — router-ът минава на следващия.
     */
    boolean isAvailable();

    /**
     * Изпраща system + user промпт и връща текстовия отговор.
     *
     * @param systemPrompt инструкцията/ролята (от Prompt Manager)
     * @param userPrompt   структуриран, PII-free вход (профил на лийда)
     * @return текстов отговор от модела
     * @throws RuntimeException при мрежова/API грешка (router-ът лови и пробва следващия)
     */
    String complete(String systemPrompt, String userPrompt);
}
