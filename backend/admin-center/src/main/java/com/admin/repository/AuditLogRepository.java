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
    
    @Query("SELECT a.userId, COUNT(a) FROM AuditLog a WHERE a.action = 'USER_LOGIN_FAILED' AND a.timestamp >= :since GROUP BY a.userId HAVING COUNT(a) >= :threshold")
    List<Object[]> findSuspiciousLoginAttempts(@Param("since") Instant since, @Param("threshold") long threshold);
    
    long countByActionAndTimestampAfter(AuditAction action, Instant since);
    
    long countByUserIdAndActionAndTimestampAfter(String userId, AuditAction action, Instant since);
}
