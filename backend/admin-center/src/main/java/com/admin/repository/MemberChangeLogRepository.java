package com.admin.repository;

import com.admin.entity.MemberChangeLog;
import com.admin.enums.ApproverTargetType;
import com.admin.enums.MemberChangeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 成员变更记录仓库接口
 */
@Repository
public interface MemberChangeLogRepository extends JpaRepository<MemberChangeLog, String> {
    
    /**
     * 根据用户ID查找变更记录
     */
    List<MemberChangeLog> findByUserId(String userId);
    
    /**
     * 根据用户ID分页查找变更记录
     */
    Page<MemberChangeLog> findByUserId(String userId, Pageable pageable);
    
    /**
     * 根据目标类型和目标ID查找变更记录
     */
    List<MemberChangeLog> findByTargetTypeAndTargetId(ApproverTargetType targetType, String targetId);
    
    /**
     * 根据变更类型查找记录
     */
    List<MemberChangeLog> findByChangeType(MemberChangeType changeType);
    
    /**
     * 根据操作人ID查找变更记录
     */
    List<MemberChangeLog> findByOperatorId(String operatorId);
    
    /**
     * 根据用户ID查找变更记录（包含用户和操作人信息）
     */
    @Query("SELECT mcl FROM MemberChangeLog mcl " +
           "LEFT JOIN FETCH mcl.user " +
           "LEFT JOIN FETCH mcl.operator " +
           "WHERE mcl.userId = :userId ORDER BY mcl.createdAt DESC")
    List<MemberChangeLog> findByUserIdWithDetails(@Param("userId") String userId);
    
    /**
     * 根据目标查找变更记录（包含用户和操作人信息）
     */
    @Query("SELECT mcl FROM MemberChangeLog mcl " +
           "LEFT JOIN FETCH mcl.user " +
           "LEFT JOIN FETCH mcl.operator " +
           "WHERE mcl.targetType = :targetType AND mcl.targetId = :targetId " +
           "ORDER BY mcl.createdAt DESC")
    List<MemberChangeLog> findByTargetWithDetails(
            @Param("targetType") ApproverTargetType targetType, 
            @Param("targetId") String targetId);
    
    /**
     * 分页查询变更记录（带筛选条件）
     */
    @Query("SELECT mcl FROM MemberChangeLog mcl " +
           "LEFT JOIN FETCH mcl.user " +
           "WHERE (:changeType IS NULL OR mcl.changeType = :changeType) " +
           "AND (:targetType IS NULL OR mcl.targetType = :targetType) " +
           "AND (:userId IS NULL OR mcl.userId = :userId) " +
           "AND (:startDate IS NULL OR mcl.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR mcl.createdAt <= :endDate)")
    Page<MemberChangeLog> findByConditions(
            @Param("changeType") MemberChangeType changeType,
            @Param("targetType") ApproverTargetType targetType,
            @Param("userId") String userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);
    
    /**
     * 统计用户的变更记录数量
     */
    long countByUserId(String userId);
    
    /**
     * 统计目标的变更记录数量
     */
    long countByTargetTypeAndTargetId(ApproverTargetType targetType, String targetId);
}
