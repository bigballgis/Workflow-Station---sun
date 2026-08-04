package com.developer.repository;

import com.developer.entity.AiPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AI 提示词覆盖值存取（每个 phase 至多一行）
 */
@Repository
public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, Long> {

    Optional<AiPromptTemplate> findByPhase(String phase);

    void deleteByPhase(String phase);
}
