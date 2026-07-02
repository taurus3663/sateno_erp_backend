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

    /**
     * „Количка без каса" за период: имало е добавяне в количка, но никога не е стигнало до каса
     * и няма поръчка. Показва само сесии със запазена снимка на количката.
     */
    @Query("SELECT s FROM LiveSessionEntity s WHERE s.addToCarts > 0 AND s.checkoutStarts = 0 " +
            "AND s.orders = 0 AND s.productsJson IS NOT NULL " +
            "AND s.lastSeen BETWEEN :from AND :to ORDER BY s.lastSeen DESC")
    List<LiveSessionEntity> findCartsWithoutCheckout(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * „Каса без данни" за период: стигнало е до каса, но без въведени контакти и без поръчка.
     * (Касите С данни се пазят отделно в live_abandoned_checkout — не се дублират тук.)
     */
    @Query("SELECT s FROM LiveSessionEntity s WHERE s.checkoutStarts > 0 AND s.orders = 0 " +
            "AND s.name IS NULL AND s.phone IS NULL AND s.email IS NULL AND s.productsJson IS NOT NULL " +
            "AND s.lastSeen BETWEEN :from AND :to ORDER BY s.lastSeen DESC")
    List<LiveSessionEntity> findCheckoutsWithoutData(@Param("from") Instant from, @Param("to") Instant to);

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
