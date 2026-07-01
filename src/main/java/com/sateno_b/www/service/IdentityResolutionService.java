package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.LiveEventDto;
import com.sateno_b.www.model.entity.CustomerEntity;
import com.sateno_b.www.model.entity.CustomerIdentityLinkEntity;
import com.sateno_b.www.model.entity.LiveSessionEntity;
import com.sateno_b.www.model.enums.IdentityMatchType;
import com.sateno_b.www.model.repository.CustomerIdentityLinkRepository;
import com.sateno_b.www.model.repository.CustomerRepository;
import com.sateno_b.www.model.repository.LiveSessionRepository;
import com.sateno_b.www.model.repository.LiveVisitorEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Слой за идентичност: свързва анонимен посетител (сесия) с реален клиент
 * (отложеният §7.7 на AI Sales Assistant).
 *
 * Стратегия (решения на Асан):
 *  - Матч срещу СЪЩЕСТВУВАЩИ клиенти по телефон (suffix, както поръчковия поток) с имейл резерва.
 *  - LINK-ONLY: НЕ създаваме нови клиенти (създаването остава в поръчковия поток → без дубликати).
 *    Абонираните лийдове без клиент пазят контакта си в live_session и се свързват после (backfill),
 *    когато поръчат и клиент бъде създаден.
 *  - Приписване ПО СЕСИЯ: при свързване стъмпваме сесията + нейните събития към клиента.
 *  - Споделено устройство: ако сесия вече е свързана с друг клиент — НЕ презаписваме; пишем
 *    маркиран (неактивен) link за одит и логваме конфликт.
 *  - Автоматично свързване само при 100% (телефон/имейл/поръчка).
 *  - Задържане: анонимни сесии/събития без клиент се трият след N дни (по подразбиране 90).
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class IdentityResolutionService {

    private final LiveSessionRepository sessionRepository;
    private final LiveVisitorEventRepository visitorEventRepository;
    private final CustomerRepository customerRepository;
    private final CustomerIdentityLinkRepository linkRepository;

    @Value("${identity.anonymous.retention-days:90}")
    private int retentionDays;
    @Value("${identity.backfill.enabled:true}")
    private boolean backfillEnabled;
    @Value("${identity.retention.enabled:true}")
    private boolean retentionEnabled;
    @Value("${identity.backfill.batch-size:500}")
    private int backfillBatchSize;

    // ---------------- извикване от ingest потока ----------------

    /**
     * Опит за свързване при значимо събитие с контакт (checkout_data / order_complete).
     * Извиква се от {@link LiveHistoryService} след като сесията е обновена.
     * Тихо при липса на съвпадение (link-only).
     */
    @Transactional
    public void tryResolveFromEvent(Long siteId, LiveEventDto e) {
        if (siteId == null || e == null || e.getSession() == null) return;
        boolean hasContact = notBlank(e.getPhone()) || notBlank(e.getEmail());
        boolean isOrder = "order_complete".equals(e.getType());
        if (!hasContact && !isOrder) return;

        // Не хвърляме навън — идентичността е вторична; при грешка само логваме,
        // за да не разваляме основния запис на историята.
        try {
            sessionRepository.findBySiteIdAndSessionToken(siteId, e.getSession())
                    .ifPresent(session -> resolveSession(session,
                            isOrder ? IdentityMatchType.ORDER : null));
        } catch (Exception ex) {
            log.warn("Identity: неуспешно свързване за сесия {}: {}", e.getSession(), ex.getMessage());
        }
    }

    /**
     * Свързва сесията с клиент, ако намери 100% съвпадение по телефон/имейл.
     * @param preferredType предпочитан match_type (напр. ORDER); ако null — определя се от съвпадението
     */
    @Transactional
    public void resolveSession(LiveSessionEntity session, IdentityMatchType preferredType) {
        Match match = findMatchingCustomer(session.getPhone(), session.getEmail());
        if (match == null) return; // link-only: няма съществуващ клиент — не създаваме

        Long matchedId = match.customer().getId();
        IdentityMatchType type = preferredType != null ? preferredType : match.type();

        Long current = session.getCustomerId();
        if (current == null) {
            linkSessionToCustomer(session, matchedId, type, 100, null);
        } else if (current.equals(matchedId)) {
            ensureLinkRow(session, matchedId, type, 100, null); // идемпотентно
        } else {
            // Конфликт (споделено устройство): не презаписваме — само одит + лог.
            ensureLinkRow(session, matchedId, type, 100, false,
                    "конфликт: сесията вече е свързана с клиент " + current);
            log.warn("Identity: конфликт за сесия {} — вече клиент {}, ново съвпадение {}. Оставям стария (по сесия).",
                    session.getSessionToken(), current, matchedId);
        }
    }

    private void linkSessionToCustomer(LiveSessionEntity session, Long customerId,
                                       IdentityMatchType type, int confidence, String note) {
        session.setCustomerId(customerId);
        sessionRepository.save(session);
        int stamped = visitorEventRepository.stampCustomer(
                session.getSiteId(), session.getSessionToken(), customerId);
        ensureLinkRow(session, customerId, type, confidence, note);
        log.info("Identity: сесия {} → клиент {} (по {}), стъмпнати {} събития",
                session.getSessionToken(), customerId, type, stamped);
    }

    private void ensureLinkRow(LiveSessionEntity session, Long customerId,
                               IdentityMatchType type, int confidence, String note) {
        ensureLinkRow(session, customerId, type, confidence, true, note);
    }

    private void ensureLinkRow(LiveSessionEntity session, Long customerId,
                               IdentityMatchType type, int confidence, boolean active, String note) {
        if (active && linkRepository.existsBySiteIdAndSessionTokenAndCustomerIdAndActiveTrue(
                session.getSiteId(), session.getSessionToken(), customerId)) {
            return;
        }
        CustomerIdentityLinkEntity link = new CustomerIdentityLinkEntity();
        link.setSiteId(session.getSiteId());
        link.setSessionToken(session.getSessionToken());
        link.setCustomerId(customerId);
        link.setMatchType(type);
        link.setConfidence(confidence);
        link.setActive(active);
        link.setNote(note);
        linkRepository.save(link);
    }

    // ---------------- матч срещу съществуващи клиенти ----------------

    private Match findMatchingCustomer(String phone, String email) {
        String suffix = normalizePhoneSuffix(phone);
        if (suffix != null) {
            List<CustomerEntity> byPhone = customerRepository.findByPhoneSuffix(suffix);
            if (!byPhone.isEmpty()) {
                return new Match(byPhone.get(0), IdentityMatchType.PHONE);
            }
        }
        String em = normalizeEmail(email);
        if (em != null) {
            Optional<CustomerEntity> byEmail = customerRepository.findByEmailLatest(em);
            if (byEmail.isPresent()) {
                return new Match(byEmail.get(), IdentityMatchType.EMAIL);
            }
        }
        return null;
    }

    /** Значещите цифри на телефона (за suffix матч): маха не-цифри, взима последните 9. */
    String normalizePhoneSuffix(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 8) return null; // твърде къс/невалиден
        return digits.length() > 9 ? digits.substring(digits.length() - 9) : digits;
    }

    String normalizeEmail(String email) {
        if (email == null) return null;
        String e = email.trim().toLowerCase();
        return e.contains("@") ? e : null;
    }

    // ---------------- нощен backfill + задържане ----------------

    /** Свързва несвързани сесии с контакт, чийто телефон/имейл вече съвпада с клиент. */
    @Transactional
    public int backfillUnlinked() {
        List<LiveSessionEntity> sessions =
                sessionRepository.findUnlinkedWithContact(PageRequest.of(0, backfillBatchSize));
        int linked = 0;
        for (LiveSessionEntity s : sessions) {
            Match m = findMatchingCustomer(s.getPhone(), s.getEmail());
            if (m != null) {
                linkSessionToCustomer(s, m.customer().getId(), m.type(), 100, null);
                linked++;
            }
        }
        return linked;
    }

    /** Задържане: трие анонимни (несвързани) събития и сесии по-стари от N дни. Връща [събития, сесии]. */
    @Transactional
    public int[] purgeAnonymous() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int events = visitorEventRepository.deleteAnonymousOlderThan(cutoff);
        int sessions = sessionRepository.deleteAnonymousOlderThan(cutoff);
        return new int[]{events, sessions};
    }

    /** Нощен backfill в 02:30 (преди Lead Score в 03:00), за да влязат новите връзки в скора. */
    @Scheduled(cron = "0 30 2 * * *", zone = "Europe/Sofia")
    public void scheduledBackfill() {
        if (!backfillEnabled) return;
        int n = backfillUnlinked();
        if (n > 0) log.info("Identity: нощен backfill свърза {} сесии", n);
    }

    /** Нощно задържане в 04:00 — трие анонимните стари данни (90 дни). */
    @Scheduled(cron = "0 0 4 * * *", zone = "Europe/Sofia")
    public void scheduledPurge() {
        if (!retentionEnabled) return;
        int[] r = purgeAnonymous();
        if (r[0] > 0 || r[1] > 0)
            log.info("Identity: задържане изтри {} събития и {} анонимни сесии (>{}д)", r[0], r[1], retentionDays);
    }

    // ---------------- ръчно раз-свързване ----------------

    /** Раз-свързва грешна връзка: маркира link неактивен и връща сесията/събитията към „анонимни". */
    @Transactional
    public CustomerIdentityLinkEntity unlink(Long linkId, String by) {
        CustomerIdentityLinkEntity link = linkRepository.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("Няма връзка с id: " + linkId));
        link.setActive(false);
        link.setUnlinkedAt(Instant.now());
        link.setUnlinkedBy(by);
        linkRepository.save(link);

        sessionRepository.findBySiteIdAndSessionToken(link.getSiteId(), link.getSessionToken())
                .filter(s -> link.getCustomerId().equals(s.getCustomerId()))
                .ifPresent(s -> {
                    s.setCustomerId(null);
                    sessionRepository.save(s);
                    visitorEventRepository.stampCustomer(s.getSiteId(), s.getSessionToken(), null);
                    log.info("Identity: раз-свързана сесия {} от клиент {} (от {})",
                            s.getSessionToken(), link.getCustomerId(), by);
                });
        return link;
    }

    /** Резултат от матч: клиент + признак. */
    private record Match(CustomerEntity customer, IdentityMatchType type) {}

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
