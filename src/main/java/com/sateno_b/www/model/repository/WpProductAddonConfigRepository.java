package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.SiteEntity;
import com.sateno_b.www.model.entity.WpAddonValueEntity;
import com.sateno_b.www.model.entity.WpProductAddonConfigEntity;
import com.sateno_b.www.model.entity.WpProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WpProductAddonConfigRepository extends JpaRepository<WpProductAddonConfigEntity, Long> {

    // Търсим по обекти (Entities), което е най-чистият начин в JPA
    Optional<WpProductAddonConfigEntity> findByProductAndAddonValueAndSite(
            WpProductEntity product,
            WpAddonValueEntity addonValue,
            SiteEntity site
    );


     List<WpProductAddonConfigEntity> findAllByProductAndSite(WpProductEntity product, SiteEntity site);
}
