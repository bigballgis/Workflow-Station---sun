package com.developer.repository;

import com.developer.entity.AiDocument;
import com.developer.enums.AiDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI 生成文档仓库
 */
@Repository
public interface AiDocumentRepository extends JpaRepository<AiDocument, Long> {

    Optional<AiDocument> findTopByFunctionUnitIdAndDocumentTypeOrderByVersionDesc(Long functionUnitId, AiDocumentType documentType);

    List<AiDocument> findByFunctionUnitIdAndDocumentTypeOrderByVersionDesc(Long functionUnitId, AiDocumentType documentType);

    Optional<AiDocument> findByFunctionUnitIdAndDocumentTypeAndVersion(Long functionUnitId, AiDocumentType documentType, Integer version);
}
