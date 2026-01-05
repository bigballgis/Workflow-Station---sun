package com.admin.repository;

import com.admin.entity.PermissionChangeHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 权限变更历史仓库接口
 */
@Repository
public interface PermissionChangeHistoryRepository extends JpaRepository<PermissionChangeHistory, String> {
    
    /**
     * 根据用户ID查找变更历史
     */
    List<PermissionChangeHistory> findByTargetUserIdOrderByChangedAtDesc(String userId);
    
    /**
     * 根据角色ID查找变更历史
     */
    List<PermissionChangeHistory> findByTargetRoleIdOrderByChangedAtDesc(String roleId);
    
    /**
     * 根据变更类型查找
     */
    List<PermissionChangeHistory> findByChangeTypeOrderByChangedAtDesc(String changeType);
    
    /**
     * 根据操作人查找
     */
    List<PermissionChangeHistory> findByChangedByOrderByChangedAtDesc(String changedBy);
    
    /**
     * 根据时间范围查找
     */
    @Query("SELECT h FROM PermissionChangeHistory h WHERE h.changedAt BETWEEN :startTime AND :endTime ORDER BY h.changedAt DESC")
    List<PermissionChangeHistory> findByTimeRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
    
    /**
     * 分页查询用户的变更历史
     */
    Page<PermissionChangeHistory> findByTargetUserId(String userId, Pageable pageable);
    
    /**
     * 分页查询角色的变更历史
     */
    Page<PermissionChangeHistory> findByTargetRoleId(String roleId, Pageable pageable);
    
    /**
     * 统计用户的变更次数
     */
    long countByTargetUserId(String userId);
    
    /**
     * 统计角色的变更次数
     */
    long countByTargetRoleId(String roleId);
}
