package com.admin.repository;

import com.admin.entity.RelationTableDefinition;
import com.platform.common.enums.RelationTableStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Relation Table 定义 Repository
 */
@Repository
public interface RelationTableDefinitionRepository extends JpaRepository<RelationTableDefinition, Long> {

    /**
     * 根据表名查找表定义
     */
    Optional<RelationTableDefinition> findByTableName(String tableName);

    /**
     * 检查表名是否已存在
     */
    boolean existsByTableName(String tableName);

    /**
     * 根据状态查找表定义列表
     */
    List<RelationTableDefinition> findByStatus(RelationTableStatus status);

    /**
     * 根据状态分页查找表定义
     */
    Page<RelationTableDefinition> findByStatus(RelationTableStatus status, Pageable pageable);

    /**
     * 查找所有已启用的表定义
     */
    List<RelationTableDefinition> findByEnabledTrue();

    /**
     * 查找门户可见的表定义
     */
    List<RelationTableDefinition> findByPortalVisibleTrue();

    /**
     * 根据多个状态且 enabled=true 查找表定义
     */
    List<RelationTableDefinition> findByStatusInAndEnabledTrue(List<RelationTableStatus> statuses);

    /**
     * 查找门户可见且已启用的已部署表定义
     */
    @Query("SELECT t FROM RelationTableDefinition t WHERE t.portalVisible = true AND t.enabled = true AND t.status IN ('DEPLOYED', 'UPDATED')")
    List<RelationTableDefinition> findPortalVisibleAndDeployed();

    /**
     * 根据表名模糊查询
     */
    List<RelationTableDefinition> findByTableNameContaining(String tableName);

    /**
     * 根据ID列表查找表定义
     */
    List<RelationTableDefinition> findByIdIn(List<Long> ids);
}
