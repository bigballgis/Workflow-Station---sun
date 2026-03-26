package com.admin.repository;

import com.admin.entity.RelationFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Relation Table 字段定义 Repository
 */
@Repository
public interface RelationFieldDefinitionRepository extends JpaRepository<RelationFieldDefinition, Long> {

    /**
     * 根据表定义ID查找所有字段定义（按排序顺序）
     */
    List<RelationFieldDefinition> findByTableDefinitionIdOrderBySortOrderAsc(Long tableDefinitionId);

    /**
     * 根据表定义ID查找所有字段定义
     */
    List<RelationFieldDefinition> findByTableDefinitionId(Long tableDefinitionId);

    /**
     * 根据表定义ID删除所有字段定义
     */
    void deleteByTableDefinitionId(Long tableDefinitionId);

    /**
     * 检查表定义下是否存在指定字段名
     */
    boolean existsByTableDefinitionIdAndFieldName(Long tableDefinitionId, String fieldName);
}
