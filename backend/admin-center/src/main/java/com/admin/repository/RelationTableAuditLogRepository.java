package com.admin.repository;

import com.admin.entity.RelationTableAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Relation Table 审计日志 Repository
 */
@Repository
public interface RelationTableAuditLogRepository extends JpaRepository<RelationTableAuditLog, String>,
        JpaSpecificationExecutor<RelationTableAuditLog> {

    /**
     * 根据表ID查找审计日志
     */
    Page<RelationTableAuditLog> findByTableId(Long tableId, Pageable pageable);

    /**
     * 根据操作类型查找审计日志
     */
    Page<RelationTableAuditLog> findByAction(String action, Pageable pageable);

    /**
     * 根据操作人ID查找审计日志
     */
    Page<RelationTableAuditLog> findByOperatorId(String operatorId, Pageable pageable);

    /**
     * 根据操作时间范围查找审计日志
     */
    Page<RelationTableAuditLog> findByOperatedAtBetween(Instant start, Instant end, Pageable pageable);

    /**
     * 按表ID、操作时间、操作人、操作类型组合过滤查询
     */
    @Query("SELECT a FROM RelationTableAuditLog a WHERE " +
           "(:tableId IS NULL OR a.tableId = :tableId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:operatorId IS NULL OR a.operatorId = :operatorId) AND " +
           "(:startTime IS NULL OR a.operatedAt >= :startTime) AND " +
           "(:endTime IS NULL OR a.operatedAt <= :endTime)")
    Page<RelationTableAuditLog> findByFilters(
            @Param("tableId") Long tableId,
            @Param("action") String action,
            @Param("operatorId") String operatorId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable);

    /**
     * 根据表ID查找所有审计日志（按操作时间降序）
     */
    List<RelationTableAuditLog> findByTableIdOrderByOperatedAtDesc(Long tableId);
}
