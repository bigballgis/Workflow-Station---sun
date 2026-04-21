package com.admin.repository;

import com.admin.entity.AuditLog;
import com.admin.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String>, JpaSpecificationExecutor<AuditLog> {
    
    Page<AuditLog> findByUserId(String userId, Pageable pageable);
    
    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);
    
    Page<AuditLog> findByResourceTypeAndResourceId(String resourceType, String resourceId, Pageable pageable);
    
    Page<AuditLog> findByTimestampBetween(Instant start, Instant end, Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE a.action IN :actions AND a.timestamp >= :since")
    List<AuditLog> findSecurityEvents(@Param("actions") List<AuditAction> actions, @Param("since") Instant since);
    
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since GROUP BY a.action")
    List<Object[]> countByActionSince(@Param("since") Instant since);
    
    /**
     * 可疑登录（失败次数过多）。
     * 统一 Action 后，登录尝试记录为 resourceType='AUTH' 的 UPDATE，失败通过 success=false 区分。
     */
    @Query("SELECT a.userId, COUNT(a) FROM AuditLog a " +
           "WHERE a.resourceType = 'AUTH' AND a.success = false AND a.timestamp >= :since " +
           "GROUP BY a.userId HAVING COUNT(a) >= :threshold")
    List<Object[]> findSuspiciousLoginAttempts(@Param("since") Instant since, @Param("threshold") long threshold);
    
    long countByActionAndTimestampAfter(AuditAction action, Instant since);
    
    long countByUserIdAndActionAndTimestampAfter(String userId, AuditAction action, Instant since);

    /** 统计某用户在时间窗口内的登录失败次数（用于锁定策略） */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.resourceType = 'AUTH' AND a.success = false AND a.userId = :userId AND a.timestamp >= :since")
    long countFailedLoginsSince(@Param("userId") String userId, @Param("since") Instant since);

    /** 统计时间窗口内成功登录次数（用于 dashboard） */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.resourceType = 'AUTH' AND a.success = true AND a.timestamp >= :since")
    long countSuccessfulLoginsSince(@Param("since") Instant since);
    
    /**
     * 统计指定时间范围内某操作的不同用户数（活跃用户）
     */
    @Query("SELECT COUNT(DISTINCT a.userId) FROM AuditLog a WHERE a.action = :action AND a.timestamp >= :start AND a.timestamp < :end")
    long countDistinctUsersByActionAndTimestampBetween(@Param("action") AuditAction action, @Param("start") Instant start, @Param("end") Instant end);

    long countByActionAndTimestampBetween(AuditAction action, Instant start, Instant end);

    /** 按天统计登录（成功）活跃用户数与登录次数 */
    @Query("SELECT CAST(a.timestamp AS DATE) as day, COUNT(DISTINCT a.userId), COUNT(a) " +
           "FROM AuditLog a WHERE a.resourceType = 'AUTH' AND a.success = true AND a.timestamp >= :start AND a.timestamp < :end " +
           "GROUP BY CAST(a.timestamp AS DATE)")
    List<Object[]> countDailySuccessfulLoginStats(@Param("start") Instant start, @Param("end") Instant end);
}
