package com.admin.repository;

import com.admin.entity.PermissionConflict;
import com.admin.enums.ConflictResolutionStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 权限冲突数据访问接口
 */
@Repository
public interface PermissionConflictRepository extends JpaRepository<PermissionConflict, String> {
    
    /**
     * 根据用户ID查找权限冲突
     */
    List<PermissionConflict> findByUserId(String userId);
    
    /**
     * 根据状态查找权限冲突
     */
    List<PermissionConflict> findByStatus(String status);
    
    /**
     * 查找待解决的权限冲突
     */
    List<PermissionConflict> findByStatusIn(List<String> statuses);
    
    /**
     * 根据解决策略查找权限冲突
     */
    List<PermissionConflict> findByResolutionStrategy(ConflictResolutionStrategy strategy);
    
    /**
     * 查找需要手动解决的权限冲突
     */
    @Query("SELECT pc FROM PermissionConflict pc WHERE pc.status = 'PENDING' " +
           "AND pc.resolutionStrategy = 'MANUAL'")
    List<PermissionConflict> findManualResolutionRequired();
    
    /**
     * 根据用户和权限查找冲突
     */
    @Query("SELECT pc FROM PermissionConflict pc WHERE pc.userId = :userId " +
           "AND pc.permission.id = :permissionId AND pc.status = 'PENDING'")
    List<PermissionConflict> findPendingConflictsByUserAndPermission(
            @Param("userId") String userId,
            @Param("permissionId") String permissionId);
    
    /**
     * 统计用户的权限冲突数量
     */
    @Query("SELECT COUNT(pc) FROM PermissionConflict pc WHERE pc.userId = :userId " +
           "AND pc.status = 'PENDING'")
    long countPendingConflictsByUser(@Param("userId") String userId);
}