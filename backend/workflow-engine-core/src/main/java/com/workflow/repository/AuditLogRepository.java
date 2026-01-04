package com.workflow.repository;

import com.workflow.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志数据访问层
 * 提供审计日志的查询、统计和分析功能
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    
    /**
     * 根据用户ID查询审计日志
     */
    Page<AuditLog> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);
    
    /**
     * 根据操作类型查询审计日志
     */
    Page<AuditLog> findByOperationTypeOrderByTimestampDesc(String operationType, Pageable pageable);
    
    /**
     * 根据资源类型和资源ID查询审计日志
     */
    Page<AuditLog> findByResourceTypeAndResourceIdOrderByTimestampDesc(
        String resourceType, String resourceId, Pageable pageable);
    
    /**
     * 根据时间范围查询审计日志
     */
    Page<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
        LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
    
    /**
     * 根据风险等级查询审计日志
     */
    Page<AuditLog> findByRiskLevelOrderByTimestampDesc(String riskLevel, Pageable pageable);
    
    /**
     * 查询敏感操作的审计日志
     */
    Page<AuditLog> findByIsSensitiveTrueOrderByTimestampDesc(Pageable pageable);
    
    /**
     * 根据IP地址查询审计日志
     */
    Page<AuditLog> findByIpAddressOrderByTimestampDesc(String ipAddress, Pageable pageable);
    
    /**
     * 根据会话ID查询审计日志
     */
    List<AuditLog> findBySessionIdOrderByTimestampDesc(String sessionId);
    
    /**
     * 根据请求ID查询审计日志（同一请求的所有操作）
     */
    List<AuditLog> findByRequestIdOrderByTimestampDesc(String requestId);
    
    /**
     * 复合条件查询审计日志
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:operationType IS NULL OR a.operationType = :operationType) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) AND " +
           "(:resourceId IS NULL OR a.resourceId = :resourceId) AND " +
           "(:startTime IS NULL OR a.timestamp >= :startTime) AND " +
           "(:endTime IS NULL OR a.timestamp <= :endTime) AND " +
           "(:riskLevel IS NULL OR a.riskLevel = :riskLevel) AND " +
           "(:isSensitive IS NULL OR a.isSensitive = :isSensitive) AND " +
           "(:tenantId IS NULL OR a.tenantId = :tenantId) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findByComplexConditions(
        @Param("userId") String userId,
        @Param("operationType") String operationType,
        @Param("resourceType") String resourceType,
        @Param("resourceId") String resourceId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("riskLevel") String riskLevel,
        @Param("isSensitive") Boolean isSensitive,
        @Param("tenantId") String tenantId,
        Pageable pageable);
    
    /**
     * 统计指定时间范围内的操作次数
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.timestamp BETWEEN :startTime AND :endTime")
    long countByTimestampBetween(@Param("startTime") LocalDateTime startTime, 
                                @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计各操作类型的数量
     */
    @Query("SELECT a.operationType, COUNT(a) FROM AuditLog a " +
           "WHERE a.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY a.operationType ORDER BY COUNT(a) DESC")
    List<Object[]> countByOperationTypeAndTimestampBetween(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计各用户的操作次数
     */
    @Query("SELECT a.userId, COUNT(a) FROM AuditLog a " +
           "WHERE a.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY a.userId ORDER BY COUNT(a) DESC")
    List<Object[]> countByUserIdAndTimestampBetween(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计各风险等级的操作次数
     */
    @Query("SELECT a.riskLevel, COUNT(a) FROM AuditLog a " +
           "WHERE a.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY a.riskLevel ORDER BY COUNT(a) DESC")
    List<Object[]> countByRiskLevelAndTimestampBetween(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 查询失败的操作
     */
    @Query("SELECT a FROM AuditLog a WHERE a.operationResult = 'FAILURE' " +
           "AND a.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findFailedOperations(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime, 
        Pageable pageable);
    
    /**
     * 查询异常活跃的IP地址
     */
    @Query("SELECT a.ipAddress, COUNT(a) FROM AuditLog a " +
           "WHERE a.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY a.ipAddress HAVING COUNT(a) > :threshold " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> findActiveIpAddresses(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime,
        @Param("threshold") long threshold);
    
    /**
     * 全文搜索审计日志
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "LOWER(a.operationDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(a.resourceName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(a.errorMessage) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 删除指定时间之前的审计日志（用于数据清理）
     */
    void deleteByTimestampBefore(LocalDateTime cutoffTime);
}