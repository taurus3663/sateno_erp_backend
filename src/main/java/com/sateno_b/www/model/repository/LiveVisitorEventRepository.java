package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.LiveVisitorEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface LiveVisitorEventRepository extends JpaRepository<LiveVisitorEventEntity, Long> {

    /** Хронология на активността за една сесия (най-новите първо). */
    List<LiveVisitorEventEntity> findBySiteIdAndSessionTokenOrderByOccurredAtDesc(Long siteId, String sessionToken);

    /** Хронология по клиент (след свързване чрез слоя за идентичност). */
    List<LiveVisitorEventEntity> findByCustomerIdOrderByOccurredAtDesc(Long customerId);

    // --- слой за идентичност ---

    /** Приписва (или изчиства с null) customerId на всички събития от една сесия. */
    @Modifying
    @Query("UPDATE LiveVisitorEventEntity e SET e.customerId = :customerId " +
            "WHERE e.siteId = :siteId AND e.sessionToken = :sessionToken")
    int stampCustomer(@Param("siteId") Long siteId,
                      @Param("sessionToken") String sessionToken,
                      @Param("customerId") Long customerId);

    long countByCustomerId(Long customerId);

    /** Задържане: изтриване на анонимни (несвързани) събития, по-стари от cutoff. */
    @Modifying
    @Query("DELETE FROM LiveVisitorEventEntity e WHERE e.customerId IS NULL AND e.occurredAt < :cutoff")
    int deleteAnonymousOlderThan(@Param("cutoff") Instant cutoff);
}
