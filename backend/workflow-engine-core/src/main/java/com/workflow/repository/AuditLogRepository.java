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
 * Audit log data access layer.
 * Provides query, statistics, and analysis functions for audit logs.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    /**
     * Find audit logs by user ID.
     */
    Page<AuditLog> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    /**
     * Find audit logs by operation type.
     */
    Page<AuditLog> findByOperationTypeOrderByTimestampDesc(String operationType, Pageable pageable);

    /**
     * Find audit logs by resource type and resource ID.
     */
    Page<AuditLog> findByResourceTypeAndResourceIdOrderByTimestampDesc(
        String resourceType, String resourceId, Pageable pageable);

    /**
     * Find audit logs within a time range.
     */
    Page<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
        LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * Find audit logs by risk level.
     */
    Page<AuditLog> findByRiskLevelOrderByTimestampDesc(String riskLevel, Pageable pageable);

    /**
     * Find sensitive operation audit logs.
     */
    Page<AuditLog> findByIsSensitiveTrueOrderByTimestampDesc(Pageable pageable);

    /**
     * Find audit logs by IP address.
     */
    Page<AuditLog> findByIpAddressOrderByTimestampDesc(String ipAddress, Pageable pageable);

    /**
     * Find audit logs by session ID.
     */
    List<AuditLog> findBySessionIdOrderByTimestampDesc(String sessionId);

    /**
     * Find audit logs by request ID (all operations for the same request).
     */
    List<AuditLog> findByRequestIdOrderByTimestampDesc(String requestId);

    /**
     * Complex condition query for audit logs.
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
     * Count operations within a time range.
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.timestamp BETWEEN :startTime AND :endTime")
    long countByTimestampBetween(@Param("startTime") LocalDateTime startTime, 
                                @Param("endTime") LocalDateTime endTime);

    /**
     * Count by operation type.
     */
    @Query("SELECT a.operationType, COUNT(a) FROM AuditLog a " +
           "WHERE a.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY a.operationType ORDER BY COUNT(a) DESC")
    List<Object[]> countByOperationTypeAndTimestampBetween(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);

    /**
     * Count operations by user.
     */
    @Query("SELECT a.userId, COUNT(a) FROM AuditLog a " +
           "WHERE a.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY a.userId ORDER BY COUNT(a) DESC")
    List<Object[]> countByUserIdAndTimestampBetween(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);

    /**
     * Count operations by risk level.
     */
    @Query("SELECT a.riskLevel, COUNT(a) FROM AuditLog a " +
           "WHERE a.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY a.riskLevel ORDER BY COUNT(a) DESC")
    List<Object[]> countByRiskLevelAndTimestampBetween(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime);

    /**
     * Find failed operations.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.operationResult = 'FAILURE' " +
           "AND a.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findFailedOperations(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime, 
        Pageable pageable);

    /**
     * Find abnormally active IP addresses.
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
     * Full-text search for audit logs.
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "LOWER(a.operationDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(a.resourceName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(a.errorMessage) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Delete audit logs before the specified time (for data cleanup).
     */
    void deleteByTimestampBefore(LocalDateTime cutoffTime);
}
