package com.developer.repository;

import com.developer.entity.RelationLookupConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Relation Table Lookup 配置仓库
 */
@Repository
public interface RelationLookupConfigRepository extends JpaRepository<RelationLookupConfig, Long> {

    /**
     * 按表单ID和组件ID查询 Lookup 配置
     */
    Optional<RelationLookupConfig> findByFormIdAndComponentId(Long formId, String componentId);

    /**
     * 按表单ID查询所有 Lookup 配置
     */
    List<RelationLookupConfig> findByFormId(Long formId);
}
