package com.workflow.repository;

import com.workflow.entity.ExceptionRecord;
import com.workflow.entity.ExceptionRecord.ExceptionSeverity;
import com.workflow.entity.ExceptionRecord.ExceptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Exception record data access layer.
 */
@Repository
public interface ExceptionRecordRepository extends JpaRepository<ExceptionRecord, String> {

    /**
     * Find exception records by process instance ID.
     */
    List<ExceptionRecord> findByProcessInstanceIdOrderByOccurredTimeDesc(String processInstanceId);

    /**
     * Find exception records by task ID.
     */
    List<ExceptionRecord> findByTaskIdOrderByOccurredTimeDesc(String taskId);

    /**
     * Find exception records by status.
     */
    List<ExceptionRecord> findByStatusOrderByOccurredTimeDesc(ExceptionStatus status);

    /**
     * Find exception records by severity level.
     */
    List<ExceptionRecord> findBySeverityOrderByOccurredTimeDesc(ExceptionSeverity severity);

    /**
     * Find unresolved exception records.
     */
    List<ExceptionRecord> findByResolvedFalseOrderBySeverityDescOccurredTimeDesc();

    /**
     * Find exception records pending retry.
     */
    @Query("SELECT e FROM ExceptionRecord e WHERE e.status = 'PENDING' " +
           "AND e.retryCount < e.maxRetryCount " +
           "AND (e.nextRetryTime IS NULL OR e.nextRetryTime <= :now) " +
           "ORDER BY e.severity DESC, e.occurredTime ASC")
    List<ExceptionRecord> findPendingRetryExceptions(@Param("now") LocalDateTime now);

    /**
     * Find exception records within a time range.
     */
    List<ExceptionRecord> findByOccurredTimeBetweenOrderByOccurredTimeDesc(
            LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Find exception records by exception type.
     */
    List<ExceptionRecord> findByExceptionTypeOrderByOccurredTimeDesc(String exceptionType);

    /**
     * Find exception records with pagination.
     */
    Page<ExceptionRecord> findByStatusAndSeverity(
            ExceptionStatus status, ExceptionSeverity severity, Pageable pageable);

    /**
     * Count exceptions grouped by status.
     */
    @Query("SELECT e.status, COUNT(e) FROM ExceptionRecord e GROUP BY e.status")
    List<Object[]> countByStatus();

    /**
     * Count unresolved exceptions grouped by severity.
     */
    @Query("SELECT e.severity, COUNT(e) FROM ExceptionRecord e WHERE e.resolved = false GROUP BY e.severity")
    List<Object[]> countUnresolvedBySeverity();

    /**
     * Count exceptions within a time range.
     */
    @Query("SELECT COUNT(e) FROM ExceptionRecord e WHERE e.occurredTime BETWEEN :startTime AND :endTime")
    Long countByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * Find exceptions that need alerting.
     */
    @Query("SELECT e FROM ExceptionRecord e WHERE e.alertSent = false " +
           "AND e.severity IN ('CRITICAL', 'HIGH') " +
           "AND e.resolved = false " +
           "ORDER BY e.severity DESC, e.occurredTime ASC")
    List<ExceptionRecord> findExceptionsNeedingAlert();

    /**
     * Count exceptions by process definition key.
     */
    @Query("SELECT e.processDefinitionKey, COUNT(e) FROM ExceptionRecord e " +
           "WHERE e.occurredTime BETWEEN :startTime AND :endTime " +
           "GROUP BY e.processDefinitionKey ORDER BY COUNT(e) DESC")
    List<Object[]> countByProcessDefinitionKey(
            @Param("startTime") LocalDateTime startTime, 
            @Param("endTime") LocalDateTime endTime);

    long countByResolvedFalse();

    @Query("SELECT e.exceptionType, COUNT(e) FROM ExceptionRecord e GROUP BY e.exceptionType")
    List<Object[]> countByExceptionType();

    @Query("SELECT e.processDefinitionKey, COUNT(e) FROM ExceptionRecord e " +
           "WHERE e.processDefinitionKey IS NOT NULL GROUP BY e.processDefinitionKey ORDER BY COUNT(e) DESC")
    List<Object[]> countGroupByProcessDefinitionKey();

    @Query("SELECT COUNT(e) FROM ExceptionRecord e WHERE e.occurredTime >= :since")
    long countSince(@Param("since") LocalDateTime since);

    /**
     * Find interrupted process instances (processes with unresolved exceptions).
     */
    @Query("SELECT DISTINCT e.processInstanceId FROM ExceptionRecord e " +
           "WHERE e.resolved = false AND e.processInstanceId IS NOT NULL")
    List<String> findInterruptedProcessInstanceIds();

    /**
     * Find exception records by tenant ID.
     */
    Page<ExceptionRecord> findByTenantIdOrderByOccurredTimeDesc(String tenantId, Pageable pageable);

    /**
     * Delete resolved exception records before the specified time.
     */
    void deleteByResolvedTrueAndResolvedTimeBefore(LocalDateTime beforeTime);
}
