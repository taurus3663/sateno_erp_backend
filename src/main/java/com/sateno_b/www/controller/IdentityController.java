package com.sateno_b.www.controller;

import com.sateno_b.www.model.entity.CustomerIdentityLinkEntity;
import com.sateno_b.www.model.entity.LiveSessionEntity;
import com.sateno_b.www.model.repository.CustomerIdentityLinkRepository;
import com.sateno_b.www.model.repository.LiveSessionRepository;
import com.sateno_b.www.model.repository.LiveVisitorEventRepository;
import com.sateno_b.www.service.IdentityResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Слой за идентичност — административни/дев endpoint-и (реален път с префикс /erp).
 *
 * ЗАБЕЛЕЖКА: под /erp/ai/** → временно permitAll за локален тест (виж SecurityConfig).
 * Преди прод да се защити за оторизиран ERP потребител.
 */
@RestController
@RequiredArgsConstructor
@Log4j2
@RequestMapping("/ai/identity")
public class IdentityController {

    private final IdentityResolutionService identityService;
    private final CustomerIdentityLinkRepository linkRepository;
    private final LiveSessionRepository sessionRepository;
    private final LiveVisitorEventRepository visitorEventRepository;

    /** Последните свързвания (одит). */
    @GetMapping("/links")
    public List<CustomerIdentityLinkEntity> links() {
        return linkRepository.findTop100ByOrderByCreateTimeDesc();
    }

    /** Идентичност за конкретен клиент: активни връзки + сесии + брой събития. */
    @GetMapping("/customer/{customerId}")
    public Map<String, Object> customer(@PathVariable Long customerId) {
        List<CustomerIdentityLinkEntity> activeLinks =
                linkRepository.findByCustomerIdAndActiveTrueOrderByCreateTimeDesc(customerId);
        List<LiveSessionEntity> sessions =
                sessionRepository.findByCustomerIdOrderByLastSeenDesc(customerId);
        Map<String, Object> out = new HashMap<>();
        out.put("customerId", customerId);
        out.put("links", activeLinks);
        out.put("sessions", sessions);
        out.put("eventCount", visitorEventRepository.countByCustomerId(customerId));
        return out;
    }

    /** Ръчно раз-свързване на грешна връзка. */
    @PostMapping("/unlink/{linkId}")
    public CustomerIdentityLinkEntity unlink(@PathVariable Long linkId,
                                             @RequestParam(required = false, defaultValue = "erp-user") String by) {
        return identityService.unlink(linkId, by);
    }

    /** Ръчно пускане на backfill (иначе върви нощно в 02:30). */
    @PostMapping("/backfill")
    public Map<String, Object> backfill() {
        int n = identityService.backfillUnlinked();
        return Map.of("linked", n);
    }

    /** Ръчно задържане (иначе върви нощно в 04:00). Връща брой изтрити събития/сесии. */
    @PostMapping("/purge")
    public Map<String, Object> purge() {
        int[] r = identityService.purgeAnonymous();
        return Map.of("deletedEvents", r[0], "deletedSessions", r[1]);
    }
}
