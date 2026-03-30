package com.admin.service;

import com.admin.entity.RelationTableAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Map;

/**
 * Relation Table 审计日志服务接口
 * 记录数据变更操作（ADD、UPDATE、DELETE、STATUS_CHANGE）的审计日志
 */
public interface RelationTableAuditService {

    /**
     * 记录新增数据操作
     */
    void logAdd(Long tableId, String tableName, String rowId, Map<String, Object> newData);

    /**
     * 记录更新数据操作
     */
    void logUpdate(Long tableId, String tableName, String rowId,
                   Map<String, Object> oldData, Map<String, Object> newData);

    /**
     * 记录删除数据操作
     */
    void logDelete(Long tableId, String tableName, String rowId, Map<String, Object> oldData);

    /**
     * 记录状态变更操作（Active/Inactive）
     */
    void logStatusChange(Long tableId, String tableName, String rowId,
                         String oldStatus, String newStatus);

    /**
     * 查询审计日志（支持按操作时间、操作人、操作类型过滤）
     */
    Page<RelationTableAuditLog> queryAuditLogs(Long tableId, String action, String operatorId,
                                                Instant startTime, Instant endTime, Pageable pageable);
}
