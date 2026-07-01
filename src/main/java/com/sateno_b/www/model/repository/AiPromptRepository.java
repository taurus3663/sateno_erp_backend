package com.sateno_b.www.model.repository;

import com.sateno_b.www.model.entity.AiPromptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiPromptRepository extends JpaRepository<AiPromptEntity, Long> {

    /** Активната версия за даден ключ. */
    Optional<AiPromptEntity> findByPromptKeyAndActiveTrue(String promptKey);

    /** Всички версии за ключ, най-новите първо. */
    List<AiPromptEntity> findByPromptKeyOrderByVersionDesc(String promptKey);

    /** Има ли изобщо версия за този ключ (за seed). */
    boolean existsByPromptKey(String promptKey);

    /** Най-високата версия за ключ (за автоинкремент). */
    Optional<AiPromptEntity> findFirstByPromptKeyOrderByVersionDesc(String promptKey);
}
