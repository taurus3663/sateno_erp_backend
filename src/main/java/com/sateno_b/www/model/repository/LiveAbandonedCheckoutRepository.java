package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.LiveAbandonedCheckoutEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface LiveAbandonedCheckoutRepository extends JpaRepository<LiveAbandonedCheckoutEntity, Long> {

    List<LiveAbandonedCheckoutEntity> findByAbandonedAtGreaterThanEqualOrderByAbandonedAtDesc(Instant from);

    List<LiveAbandonedCheckoutEntity> findByAbandonedAtBetweenOrderByAbandonedAtDesc(Instant from, Instant to);

    /** Съществуващ lead за уникален клиент (сайт + сесиен токен) — за дедупликация/натрупване. */
    Optional<LiveAbandonedCheckoutEntity> findFirstBySiteIdAndSessionTokenOrderByAbandonedAtDesc(Long siteId, String sessionToken);

    /** Изтрива напуснатите каси за дадена сесия — при завършена поръчка. */
    void deleteBySiteIdAndSessionToken(Long siteId, String sessionToken);
}
