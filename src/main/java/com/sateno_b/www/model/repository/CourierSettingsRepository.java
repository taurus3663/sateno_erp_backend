package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.CourierSettingsEntity;
import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.enums.CourierType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourierSettingsRepository extends JpaRepository<CourierSettingsEntity, Long> {
    // Вземаме конкретна настройка (напр. Спиди за Сайт 1)
    Optional<CourierSettingsEntity> findBySiteIdAndCourierType(Long siteId, CourierType type);

    // Всички активни куриери за даден сайт
    List<CourierSettingsEntity> findAllBySiteIdAndActiveTrue(Long siteId);

    List<CourierSettingsEntity> findAllBySiteOrderBySortOrderAsc(SiteEntity site);
    List<CourierSettingsEntity> findAllBySiteAndActive(SiteEntity site, boolean active);
    List<CourierSettingsEntity> findAllBySiteAndActiveAndDefaultCourierTrue(SiteEntity site, boolean active);

    List<CourierSettingsEntity> findBySiteIdAndDefaultCourierTrue(Long attr0);

    Optional<CourierSettingsEntity> findFirstBySiteIdAndCourierTypeAndActiveTrueAndIdNot(Long id, CourierType courierType, Long id1);

    Optional<CourierSettingsEntity> findBySiteAndCourierTypeAndActiveTrueAndDefaultCourierTrue(SiteEntity site, CourierType courierType);

    Optional<CourierSettingsEntity> findBySiteIdAndCourierTypeAndActiveTrue(Long siteId, CourierType courierType);
//    Optional<CourierSettingsEntity> findBySiteAndCourierTypeAndCourierShipmentTypeAndActiveTrue(SiteEntity site, CourierType courierType, CourierShipmentType courierShipmentType);

}
