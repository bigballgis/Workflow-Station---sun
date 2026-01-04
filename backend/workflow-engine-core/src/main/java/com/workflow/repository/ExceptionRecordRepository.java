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
 * 异常记录数据访问层
 */
@Repository
public interface ExceptionRecordRepository extends JpaRepository<ExceptionRecord, String> {
    
    /**
     * 根据流程实例ID查询异常记录
     */
    List<ExceptionRecord> findByProcessInstanceIdOrderByOccurredTimeDesc(String processInstanceId);
    
    /**
     * 根据任务ID查询异常记录
     */
    List<ExceptionRecord> findByTaskIdOrderByOccurredTimeDesc(String taskId);
    
    /**
     * 根据状态查询异常记录
     */
    List<ExceptionRecord> findByStatusOrderByOccurredTimeDesc(ExceptionStatus status);
    
    /**
     * 根据严重级别查询异常记录
     */
    List<ExceptionRecord> findBySeverityOrderByOccurredTimeDesc(ExceptionSeverity severity);
    
    /**
     * 查询未解决的异常记录
     */
    List<ExceptionRecord> findByResolvedFalseOrderBySeverityDescOccurredTimeDesc();
    
    /**
     * 查询待重试的异常记录
     */
    @Query("SELECT e FROM ExceptionRecord e WHERE e.status = 'PENDING' " +
           "AND e.retryCount < e.maxRetryCount " +
           "AND (e.nextRetryTime IS NULL OR e.nextRetryTime <= :now) " +
           "ORDER BY e.severity DESC, e.occurredTime ASC")
    List<ExceptionRecord> findPendingRetryExceptions(@Param("now") LocalDateTime now);
    
    /**
     * 根据时间范围查询异常记录
     */
    List<ExceptionRecord> findByOccurredTimeBetweenOrderByOccurredTimeDesc(
            LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 根据异常类型查询
     */
    List<ExceptionRecord> findByExceptionTypeOrderByOccurredTimeDesc(String exceptionType);
    
    /**
     * 分页查询异常记录
     */
    Page<ExceptionRecord> findByStatusAndSeverity(
            ExceptionStatus status, ExceptionSeverity severity, Pageable pageable);

    /**
     * 统计各状态的异常数量
     */
    @Query("SELECT e.status, COUNT(e) FROM ExceptionRecord e GROUP BY e.status")
    List<Object[]> countByStatus();
    
    /**
     * 统计各严重级别的异常数量
     */
    @Query("SELECT e.severity, COUNT(e) FROM ExceptionRecord e WHERE e.resolved = false GROUP BY e.severity")
    List<Object[]> countUnresolvedBySeverity();
    
    /**
     * 统计指定时间范围内的异常数量
     */
    @Query("SELECT COUNT(e) FROM ExceptionRecord e WHERE e.occurredTime BETWEEN :startTime AND :endTime")
    Long countByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    /**
     * 查询需要发送告警的异常
     */
    @Query("SELECT e FROM ExceptionRecord e WHERE e.alertSent = false " +
           "AND e.severity IN ('CRITICAL', 'HIGH') " +
           "AND e.resolved = false " +
           "ORDER BY e.severity DESC, e.occurredTime ASC")
    List<ExceptionRecord> findExceptionsNeedingAlert();
    
    /**
     * 根据流程定义Key统计异常
     */
    @Query("SELECT e.processDefinitionKey, COUNT(e) FROM ExceptionRecord e " +
           "WHERE e.occurredTime BETWEEN :startTime AND :endTime " +
           "GROUP BY e.processDefinitionKey ORDER BY COUNT(e) DESC")
    List<Object[]> countByProcessDefinitionKey(
            @Param("startTime") LocalDateTime startTime, 
            @Param("endTime") LocalDateTime endTime);
    
    /**
     * 查询中断的流程实例（有未解决异常的流程）
     */
    @Query("SELECT DISTINCT e.processInstanceId FROM ExceptionRecord e " +
           "WHERE e.resolved = false AND e.processInstanceId IS NOT NULL")
    List<String> findInterruptedProcessInstanceIds();
    
    /**
     * 根据租户ID查询异常记录
     */
    Page<ExceptionRecord> findByTenantIdOrderByOccurredTimeDesc(String tenantId, Pageable pageable);
    
    /**
     * 删除指定时间之前的已解决异常记录
     */
    void deleteByResolvedTrueAndResolvedTimeBefore(LocalDateTime beforeTime);
}
