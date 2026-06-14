package com.admin.service.impl;

import com.admin.audit.AuditActorResolver;
import com.admin.audit.AuditContextHolder;
import com.admin.entity.RelationTableAuditLog;
import com.admin.repository.RelationTableAuditLogRepository;
import com.admin.repository.UserRepository;
import com.admin.service.RelationTableAuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.enums.RelationAuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Relation Table 审计日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationTableAuditServiceImpl implements RelationTableAuditService {

    private final RelationTableAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void logAdd(Long tableId, String tableName, String rowId, Map<String, Object> newData) {
        log.info("Logging ADD audit for table: {}, rowId: {}", tableName, rowId);
        saveAuditLog(tableId, tableName, rowId, RelationAuditAction.ADD, null, toJson(newData));
    }

    @Override
    @Transactional
    public void logUpdate(Long tableId, String tableName, String rowId,
                          Map<String, Object> oldData, Map<String, Object> newData) {
        log.info("Logging UPDATE audit for table: {}, rowId: {}", tableName, rowId);
        saveAuditLog(tableId, tableName, rowId, RelationAuditAction.UPDATE, toJson(oldData), toJson(newData));
    }

    @Override
    @Transactional
    public void logDelete(Long tableId, String tableName, String rowId, Map<String, Object> oldData) {
        log.info("Logging DELETE audit for table: {}, rowId: {}", tableName, rowId);
        saveAuditLog(tableId, tableName, rowId, RelationAuditAction.DELETE, toJson(oldData), null);
    }

    @Override
    @Transactional
    public void logStatusChange(Long tableId, String tableName, String rowId,
                                String oldStatus, String newStatus) {
        log.info("Logging STATUS_CHANGE audit for table: {}, rowId: {}, {} -> {}", tableName, rowId, oldStatus, newStatus);
        Map<String, Object> oldValue = Map.of("status", oldStatus);
        Map<String, Object> newValue = Map.of("status", newStatus);
        saveAuditLog(tableId, tableName, rowId, RelationAuditAction.STATUS_CHANGE, toJson(oldValue), toJson(newValue));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RelationTableAuditLog> queryAuditLogs(Long tableId, String action, String operatorId,
                                                       Instant startTime, Instant endTime, Pageable pageable) {
        return auditLogRepository.findByFilters(tableId, action, operatorId, startTime, endTime, pageable);
    }

    // ==================== 辅助方法 ====================

    private void saveAuditLog(Long tableId, String tableName, String rowId,
                              RelationAuditAction action, String oldValue, String newValue) {
        AuditContextHolder.AuditContext ctx = AuditContextHolder.get();
        String rawUserId = AuditActorResolver.resolveUserId(ctx);
        String rawUserName = AuditActorResolver.resolveUserName(ctx, rawUserId, userRepository);
        AuditActorResolver.OperatorIdentity operator = AuditActorResolver.normalizeOperator(
                rawUserId, rawUserName, userRepository);

        RelationTableAuditLog auditLog = RelationTableAuditLog.builder()
                .tableId(tableId)
                .tableName(tableName)
                .rowId(rowId)
                .action(action.getCode())
                .oldValue(oldValue)
                .newValue(newValue)
                .operatorId(operator.userId())
                .operatorName(operator.userName())
                .operatedAt(Instant.now())
                .build();

        auditLogRepository.save(auditLog);
    }

    private String toJson(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit data to JSON: {}", e.getMessage());
            return data.toString();
        }
    }
}
