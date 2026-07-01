package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.CustomerIdentityLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerIdentityLinkRepository extends JpaRepository<CustomerIdentityLinkEntity, Long> {

    boolean existsBySiteIdAndSessionTokenAndCustomerIdAndActiveTrue(
            Long siteId, String sessionToken, Long customerId);

    Optional<CustomerIdentityLinkEntity> findFirstBySiteIdAndSessionTokenAndActiveTrueOrderByCreateTimeAsc(
            Long siteId, String sessionToken);

    List<CustomerIdentityLinkEntity> findByCustomerIdAndActiveTrueOrderByCreateTimeDesc(Long customerId);

    List<CustomerIdentityLinkEntity> findTop100ByOrderByCreateTimeDesc();
}
