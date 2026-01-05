package com.developer.repository;

import com.developer.entity.FieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 字段定义仓库
 */
@Repository
public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, Long> {
    
    List<FieldDefinition> findByTableDefinitionIdOrderBySortOrderAsc(Long tableId);
    
    boolean existsByTableDefinitionIdAndFieldName(Long tableId, String fieldName);
    
    boolean existsByTableDefinitionIdAndFieldNameAndIdNot(Long tableId, String fieldName, Long id);
}
