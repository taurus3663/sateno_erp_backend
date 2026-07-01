package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.CustomerBehaviorDto;
import com.sateno_b.www.model.entity.AiPromptEntity;
import com.sateno_b.www.model.entity.AiRecommendationEntity;
import com.sateno_b.www.model.entity.CommunicationQueueEntity;
import com.sateno_b.www.model.entity.CustomerIntelligenceEntity;
import com.sateno_b.www.model.enums.RecommendationChannel;
import com.sateno_b.www.model.repository.CustomerIntelligenceRepository;
import com.sateno_b.www.service.AiPromptService;
import com.sateno_b.www.service.AiRecommendationService;
import com.sateno_b.www.service.CommunicationQueueService;
import com.sateno_b.www.service.CustomerBehaviorService;
import com.sateno_b.www.service.LeadScoreService;
import com.sateno_b.www.service.RecommendationApprovalService;
import com.sateno_b.www.service.ai.AiProvider;
import com.sateno_b.www.service.ai.AiProviderRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI Sales Assistant — административни/дев endpoint-и (Фази 2–4).
 * Реалният път е с префикс /erp: напр. /erp/ai/leads.
 *
 * ЗАБЕЛЕЖКА: тези пътища са временно permitAll за локален тест (виж SecurityConfig).
 * Преди прод да се защитят за оторизиран ERP потребител.
 */
@RestController
@RequiredArgsConstructor
@Log4j2
@RequestMapping("/ai")
public class AiSalesController {

    private final LeadScoreService leadScoreService;
    private final CustomerIntelligenceRepository intelligenceRepository;
    private final AiRecommendationService recommendationService;
    private final AiPromptService promptService;
    private final AiProviderRouter providerRouter;
    private final RecommendationApprovalService approvalService;
    private final CommunicationQueueService queueService;
    private final CustomerBehaviorService customerBehaviorService;

    // ---------------- Фаза 2: Lead Score ----------------

    /** Ръчно преизчисляване на Lead Score (за тест; иначе върви нощно ~03:00). */
    @PostMapping("/recompute-leads")
    public Map<String, Object> recompute() {
        int n = leadScoreService.recomputeAll();
        return Map.of("recomputed", n);
    }

    /** Топ лийдове по Lead Score. */
    @GetMapping("/leads")
    public List<CustomerIntelligenceEntity> topLeads() {
        return intelligenceRepository.findTop50ByOrderByLeadScoreDesc();
    }

    /** Дашборд „Поведение на клиента" — агрегиран профил за конкретен клиент. */
    @GetMapping("/customer-behavior/{customerId}")
    public CustomerBehaviorDto customerBehavior(@PathVariable Long customerId) {
        return customerBehaviorService.build(customerId);
    }

    // ---------------- Фаза 3: AI препоръки ----------------

    /**
     * Генерира AI препоръки (чернови) за лийдове.
     * Обхватът е сменяем: {@code minScore} (по подразбиране 40 = горещи+топли), {@code limit}.
     * {@code dryRun=true} само проверява обхвата и избрания доставчик, без AI повик/запис.
     */
    @PostMapping("/generate-recommendations")
    public AiRecommendationService.GenerateSummary generateRecommendations(
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "false") boolean dryRun) {
        return recommendationService.generate(minScore, limit, dryRun);
    }

    /** Последните генерирани препоръки. */
    @GetMapping("/recommendations")
    public List<AiRecommendationEntity> recommendations() {
        return recommendationService.recent();
    }

    /** Само чакащите одобрение (за таблото). */
    @GetMapping("/recommendations/pending")
    public List<AiRecommendationEntity> pendingRecommendations() {
        return recommendationService.pending();
    }

    // ---------------- Фаза 3: Prompt Manager ----------------

    /** Всички промптове (всички ключове и версии). */
    @GetMapping("/prompts")
    public List<AiPromptEntity> prompts() {
        return promptService.all();
    }

    /** Версиите за конкретен ключ (най-новите първо). */
    @GetMapping("/prompts/{key}/versions")
    public List<AiPromptEntity> promptVersions(@PathVariable String key) {
        return promptService.versions(key);
    }

    /** Създава нова версия на промпт и я активира. */
    @PostMapping("/prompts")
    public AiPromptEntity createPrompt(@RequestBody Map<String, String> req) {
        String key = req.getOrDefault("key", AiPromptService.KEY_SALES_RECOMMENDATION);
        String body = req.get("body");
        String description = req.get("description");
        return promptService.saveNewVersion(key, body, description);
    }

    /** Активира конкретна версия (по id). */
    @PostMapping("/prompts/{id}/activate")
    public AiPromptEntity activatePrompt(@PathVariable Long id) {
        return promptService.activate(id);
    }

    // ---------------- Фаза 4: Approval Workflow ----------------

    /** Редакция на чернова (текст и/или канал), докато чака одобрение. */
    @PutMapping("/recommendations/{id}")
    public AiRecommendationEntity editRecommendation(@PathVariable Long id,
                                                     @RequestBody Map<String, String> req) {
        String text = req.get("text");
        RecommendationChannel channel = parseChannel(req.get("channel"));
        return approvalService.edit(id, text, channel);
    }

    /**
     * Одобрява чернова → влиза в Communication Queue (не се изпраща — Фаза 4).
     * Тяло (по избор): {@code text}, {@code channel}, {@code decidedBy}.
     */
    @PostMapping("/recommendations/{id}/approve")
    public AiRecommendationEntity approve(@PathVariable Long id,
                                          @RequestBody(required = false) Map<String, String> req) {
        Map<String, String> body = (req != null) ? req : Map.of();
        return approvalService.approve(
                id, body.get("text"), parseChannel(body.get("channel")), body.get("decidedBy"));
    }

    /** Отхвърля чернова. Тяло (по избор): {@code reason}, {@code decidedBy}. */
    @PostMapping("/recommendations/{id}/reject")
    public AiRecommendationEntity reject(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, String> req) {
        Map<String, String> body = (req != null) ? req : Map.of();
        return approvalService.reject(id, body.get("reason"), body.get("decidedBy"));
    }

    // ---------------- Фаза 4: Communication Queue ----------------

    /** Опашка за комуникация (последните записи). */
    @GetMapping("/queue")
    public List<CommunicationQueueEntity> queue() {
        return queueService.recent();
    }

    /** Само чакащите изпращане (QUEUED). */
    @GetMapping("/queue/pending")
    public List<CommunicationQueueEntity> queuePending() {
        return queueService.queued();
    }

    /** Статус на опашката/изпращането (за таблото и проверка). */
    @GetMapping("/queue/status")
    public Map<String, Object> queueStatus() {
        return Map.of(
                "queued", queueService.countQueued(),
                "sendingEnabled", queueService.isSendingEnabled()
        );
    }

    // ---------------- Диагностика ----------------

    private RecommendationChannel parseChannel(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return RecommendationChannel.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    /** Статус на AI доставчиците (кой е наличен) — за проверка на конфигурацията. */
    @GetMapping("/providers")
    public Map<String, Object> providers() {
        List<Map<String, Object>> list = providerRouter.all().stream()
                .map(p -> Map.<String, Object>of("name", p.name(), "available", p.isAvailable()))
                .toList();
        // HashMap (не Map.of) — "primary" може да е null, а Map.of не приема null стойности.
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("providers", list);
        out.put("anyAvailable", providerRouter.anyAvailable());
        out.put("primary", providerRouter.primary().map(AiProvider::name).orElse(null));
        return out;
    }
}
