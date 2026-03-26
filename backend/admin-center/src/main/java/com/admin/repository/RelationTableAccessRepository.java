package com.admin.repository;

import com.admin.entity.RelationTableAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Relation Table 访问权限 Repository
 */
@Repository
public interface RelationTableAccessRepository extends JpaRepository<RelationTableAccess, String> {

    /**
     * 根据表ID查找所有访问配置
     */
    List<RelationTableAccess> findByTableId(Long tableId);

    /**
     * 根据目标ID查找所有访问配置（如按角色ID查找）
     */
    List<RelationTableAccess> findByTargetId(String targetId);

    /**
     * 根据目标类型和目标ID查找访问配置
     */
    List<RelationTableAccess> findByTargetTypeAndTargetId(String targetType, String targetId);

    /**
     * 删除表的所有访问配置
     */
    void deleteByTableId(Long tableId);

    /**
     * 检查是否存在特定的访问配置
     */
    boolean existsByTableIdAndTargetTypeAndTargetId(Long tableId, String targetType, String targetId);

    /**
     * 查询用户可访问的表ID列表（通过角色）
     */
    @Query("SELECT DISTINCT a.tableId FROM RelationTableAccess a WHERE a.targetType = 'ROLE' AND a.targetId IN :roleIds")
    List<Long> findAccessibleTableIdsByRoles(@Param("roleIds") List<String> roleIds);
}
