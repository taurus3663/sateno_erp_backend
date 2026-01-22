package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.WpAddonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WpAddonRepository extends JpaRepository<WpAddonEntity, Long> {

    Optional<WpAddonEntity> findBySlug(String slug);

}
