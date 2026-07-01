package com.sateno_b.www.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sateno_b.www.model.entity.AiPromptEntity;
import com.sateno_b.www.model.entity.AiRecommendationEntity;
import com.sateno_b.www.model.entity.CustomerIntelligenceEntity;
import com.sateno_b.www.model.enums.RecommendationChannel;
import com.sateno_b.www.model.enums.RecommendationStatus;
import com.sateno_b.www.model.repository.AiRecommendationRepository;
import com.sateno_b.www.model.repository.CustomerIntelligenceRepository;
import com.sateno_b.www.service.ai.AiProviderRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Генерира AI препоръки в ЧЕРНОВА (Фаза 3 на AI Sales Assistant).
 *
 * Поток за всеки лийд:
 *   1) взима профила (Customer Intelligence) — БЕЗ лични данни,
 *   2) зарежда активния промпт от Prompt Manager,
 *   3) вика AI през {@link AiProviderRouter} (Claude → OpenAI fallback),
 *   4) парсва JSON отговора и записва {@link AiRecommendationEntity} със статус PENDING_APPROVAL.
 *
 * НИЩО не се изпраща — само чернова за одобрение (Фаза 4).
 * Обхватът (кои лийдове) е сменяем чрез {@code minScore} — по подразбиране 40 (горещи+топли).
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class AiRecommendationService {

    /** По подразбиране: горещи + топли (score ≥ 40). Сменяемо на повикването. */
    public static final int DEFAULT_MIN_SCORE = 40;
    private static final int DEFAULT_LIMIT = 50;
    /** Пропускаме клиент, ако вече има чернова, чакаща одобрение, от последните N часа. */
    private static final long DEDUP_HOURS = 24;

    private final CustomerIntelligenceRepository intelligenceRepository;
    private final AiRecommendationRepository recommendationRepository;
    private final AiPromptService promptService;
    private final AiProviderRouter router;
    private final ObjectMapper objectMapper;

    /**
     * Генерира препоръки за лийдове със score ≥ minScore.
     *
     * @param minScore праг (null → {@link #DEFAULT_MIN_SCORE})
     * @param limit    макс брой лийдове за обработка (null → {@link #DEFAULT_LIMIT})
     * @param dryRun   ако true — само пресмята обхвата и избрания доставчик, без AI повик/запис
     */
    @Transactional
    public GenerateSummary generate(Integer minScore, Integer limit, boolean dryRun) {
        int min = (minScore != null) ? minScore : DEFAULT_MIN_SCORE;
        int max = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;

        List<CustomerIntelligenceEntity> leads =
                intelligenceRepository.findByLeadScoreGreaterThanEqualOrderByLeadScoreDesc(min);
        if (leads.size() > max) {
            leads = leads.subList(0, max);
        }

        GenerateSummary summary = new GenerateSummary();
        summary.minScore = min;
        summary.limit = max;
        summary.candidates = leads.size();
        summary.providerAvailable = router.anyAvailable();
        summary.dryRun = dryRun;

        if (dryRun) {
            router.primary().ifPresent(p -> summary.provider = p.name());
            return summary;
        }

        if (!summary.providerAvailable) {
            summary.message = "Няма конфигуриран AI доставчик (липсва API ключ). "
                    + "Сложи ANTHROPIC_API_KEY в env или ползвай OpenAI fallback.";
            return summary;
        }

        AiPromptEntity prompt = promptService.requireActive(AiPromptService.KEY_SALES_RECOMMENDATION);
        Instant dedupAfter = Instant.now().minus(DEDUP_HOURS, ChronoUnit.HOURS);

        for (CustomerIntelligenceEntity lead : leads) {
            if (lead.getCustomerId() == null) continue;

            boolean recent = recommendationRepository.existsByCustomerIdAndStatusAndCreateTimeAfter(
                    lead.getCustomerId(), RecommendationStatus.PENDING_APPROVAL, dedupAfter);
            if (recent) {
                summary.skipped++;
                continue;
            }

            try {
                String profileJson = buildPiiFreeProfile(lead);
                AiProviderRouter.Result result = router.complete(prompt.getBody(), profileJson);
                ParsedDraft draft = parse(result.text());

                AiRecommendationEntity rec = new AiRecommendationEntity();
                rec.setCustomerId(lead.getCustomerId());
                rec.setLeadScore(lead.getLeadScore());
                rec.setLeadTier(lead.getLeadTier());
                rec.setChannel(draft.channel());
                rec.setRecType(draft.type());
                rec.setReason(draft.reason());
                rec.setAiDraftText(draft.draft());
                rec.setStatus(RecommendationStatus.PENDING_APPROVAL);
                rec.setCreatedBy("AI:" + result.provider());
                rec.setAiProvider(result.provider());
                rec.setPromptKey(prompt.getPromptKey());
                rec.setPromptVersion(prompt.getVersion());
                recommendationRepository.save(rec);
                summary.generated++;
            } catch (Exception e) {
                summary.failed++;
                log.warn("Генерирането на препоръка за клиент {} се провали: {}",
                        lead.getCustomerId(), e.getMessage());
            }
        }
        return summary;
    }

    /**
     * Строи входа за AI — САМО ID + поведение/агрегати. Без име/телефон/имейл (решение §7.2).
     */
    private String buildPiiFreeProfile(CustomerIntelligenceEntity lead) {
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("customerId", lead.getCustomerId());
            n.put("leadScore", lead.getLeadScore());
            n.put("leadTier", lead.getLeadTier());
            n.put("ordersCount", lead.getOrdersCount());
            n.put("ordersValue", lead.getOrdersValue() != null ? lead.getOrdersValue().toPlainString() : "0");
            n.put("recencyDays", lead.getRecencyDays());
            n.put("lastOrderAt", lead.getLastOrderAt() != null ? lead.getLastOrderAt().toString() : null);
            n.put("abandonedCarts", lead.getAbandonedCarts());
            n.put("adSource", lead.getAdSource());
            return "Профил на клиент (без лични данни):\n" + objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            throw new RuntimeException("Неуспешно сериализиране на профила", e);
        }
    }

    /**
     * Парсва JSON отговора от AI. Толерантно: маха ограждащи ```json огради и взима
     * първия {...} блок. При неуспех — целият текст става чернова с канал EMAIL по подразбиране.
     */
    private ParsedDraft parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        String json = extractJson(text);
        if (json != null) {
            try {
                JsonNode node = objectMapper.readTree(json);
                RecommendationChannel channel = parseChannel(node.path("channel").asText(null));
                String type = emptyToNull(node.path("type").asText(null));
                String reason = emptyToNull(node.path("reason").asText(null));
                String draft = emptyToNull(node.path("draft").asText(null));
                if (draft == null) draft = text; // ако липсва draft, пазим суровия текст
                return new ParsedDraft(channel, type, reason, draft);
            } catch (Exception ignore) {
                // пада към резервния вариант отдолу
            }
        }
        return new ParsedDraft(RecommendationChannel.EMAIL, null,
                "(AI отговорът не беше валиден JSON — запазен като суров текст)", text);
    }

    private String extractJson(String text) {
        String t = text;
        int fence = t.indexOf("```");
        if (fence >= 0) {
            t = t.replaceAll("```json", "").replaceAll("```", "").trim();
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return null;
    }

    private RecommendationChannel parseChannel(String s) {
        if (s == null) return RecommendationChannel.EMAIL;
        try {
            return RecommendationChannel.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return RecommendationChannel.EMAIL;
        }
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // --- listing helpers ---

    public List<AiRecommendationEntity> recent() {
        return recommendationRepository.findTop100ByOrderByCreateTimeDesc();
    }

    public List<AiRecommendationEntity> pending() {
        return recommendationRepository.findByStatusOrderByLeadScoreDesc(
                RecommendationStatus.PENDING_APPROVAL);
    }

    /** Резултат от AI парсване. */
    private record ParsedDraft(RecommendationChannel channel, String type, String reason, String draft) {}

    /** Обобщение от едно пускане (за отговор към UI/дев тест). */
    public static class GenerateSummary {
        public int minScore;
        public int limit;
        public int candidates;
        public int generated;
        public int skipped;
        public int failed;
        public boolean providerAvailable;
        public String provider;
        public boolean dryRun;
        public String message;

        public List<String> notes() {
            List<String> notes = new ArrayList<>();
            notes.add("candidates=" + candidates);
            notes.add("generated=" + generated);
            notes.add("skipped=" + skipped);
            notes.add("failed=" + failed);
            return notes;
        }
    }
}
