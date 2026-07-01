package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.LiveSessionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface LiveSessionRepository extends JpaRepository<LiveSessionEntity, Long> {

    /** Съществуваща сесия за уникален посетител (сайт + сесиен токен). */
    Optional<LiveSessionEntity> findBySiteIdAndSessionToken(Long siteId, String sessionToken);

    /** Всички сесии на клиент (след като слоят за идентичност ги свърже). */
    List<LiveSessionEntity> findByCustomerIdOrderByLastSeenDesc(Long customerId);

    /** Скорошни сесии за сайт (за списъци/табло). */
    List<LiveSessionEntity> findBySiteIdAndLastSeenGreaterThanEqualOrderByLastSeenDesc(Long siteId, Instant from);

    // --- слой за идентичност ---

    /** Несвързани сесии, които имат контакт (за нощен backfill на свързването). */
    @Query("SELECT s FROM LiveSessionEntity s WHERE s.customerId IS NULL " +
            "AND (s.phone IS NOT NULL OR s.email IS NOT NULL) ORDER BY s.lastSeen DESC")
    List<LiveSessionEntity> findUnlinkedWithContact(Pageable pageable);

    /** Задържане: изтриване на анонимни (несвързани) сесии, по-стари от cutoff (90 дни). */
    @Modifying
    @Query("DELETE FROM LiveSessionEntity s WHERE s.customerId IS NULL AND s.lastSeen < :cutoff")
    int deleteAnonymousOlderThan(@Param("cutoff") Instant cutoff);
}
