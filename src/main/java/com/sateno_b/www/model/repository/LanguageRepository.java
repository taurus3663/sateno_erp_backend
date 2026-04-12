package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.LanguageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LanguageRepository extends JpaRepository<LanguageEntity, Long> {

    // Spring автоматично ще разбере, че трябва да търси по колоната "code"
    LanguageEntity findByCode(String code);
}
