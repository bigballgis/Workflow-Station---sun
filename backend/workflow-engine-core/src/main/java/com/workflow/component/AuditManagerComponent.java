package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.AuditLogQueryRequest;
import com.workflow.dto.AuditLogStatisticsResult;
import com.workflow.entity.AuditLog;
import com.workflow.enums.AuditOperationType;
import com.workflow.enums.AuditResourceType;
import com.workflow.enums.AuditRiskLevel;
import com.workflow.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审计管理组件
 * 负责审计日志的记录、查询、分析和管理
 * 支持数据脱敏、权限控制和合规检查
 */
@Component("workflowAuditManager")
public class AuditManagerComponent {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditManagerComponent.class);
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // 敏感字段列表，需要脱敏处理
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "token", "secret", "key", "credential", 
        "ssn", "idCard", "phone", "email", "bankAccount"
    );
    
    /**
     * 记录审计日志
     */
    @Transactional
    public String recordAuditLog(AuditOperationType operationType, 
                                AuditResourceType resourceType,
                                String resourceId,
                                String resourceName,
                                String userId,
                                String operationDescription,
                                Object beforeData,
                                Object afterData,
                                String operationResult,
                                String errorMessage,
                                String ipAddress,
                                String userAgent,
                                String sessionId,
                                String requestId,
                                Long durationMs,
                                String tenantId,
                                Map<String, Object> contextData) {
        
        try {
            String auditId = UUID.randomUUID().toString();
            
            AuditLog auditLog = new AuditLog();
            auditLog.setId(auditId);
            auditLog.setUserId(userId);
            auditLog.setOperationType(operationType.name());
            auditLog.setResourceType(resourceType.name());
            auditLog.setResourceId(resourceId);
            auditLog.setResourceName(resourceName);
            auditLog.setOperationDescription(operationDescription);
            auditLog.setOperationResult(operationResult);
            auditLog.setErrorMessage(errorMessage);
            auditLog.setTimestamp(LocalDateTime.now());
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setSessionId(sessionId);
            auditLog.setRequestId(requestId);
            auditLog.setDurationMs(durationMs);
            auditLog.setTenantId(tenantId);
            
            // 评估风险等级
            AuditRiskLevel riskLevel = AuditRiskLevel.evaluateRiskLevel(operationType, resourceType);
            auditLog.setRiskLevel(riskLevel.name());
            
            // 判断是否为敏感操作
            auditLog.setIsSensitive(isSensitiveOperation(operationType, resourceType));
            
            // 数据脱敏处理
            if (beforeData != null) {
                auditLog.setBeforeData(maskSensitiveData(beforeData));
            }
            if (afterData != null) {
                auditLog.setAfterData(maskSensitiveData(afterData));
            }
            if (contextData != null) {
                auditLog.setContextData(maskSensitiveData(contextData));
            }
            
            auditLogRepository.save(auditLog);
            
            logger.info("审计日志记录成功: auditId={}, operationType={}, resourceType={}, resourceId={}, userId={}", 
                       auditId, operationType, resourceType, resourceId, userId);
            
            return auditId;
            
        } catch (Exception e) {
            logger.error("记录审计日志失败: operationType={}, resourceType={}, resourceId={}, userId={}", 
                        operationType, resourceType, resourceId, userId, e);
            throw new RuntimeException("记录审计日志失败", e);
        }
    }
    
    /**
     * 简化的审计日志记录方法
     */
    public String recordAuditLog(AuditOperationType operationType, 
                                AuditResourceType resourceType,
                                String resourceId,
                                String userId,
                                String operationResult) {
        return recordAuditLog(operationType, resourceType, resourceId, null, userId, 
                             operationType.getDescription(), null, null, operationResult, 
                             null, null, null, null, null, null, null, null);
    }
    
    /**
     * 查询审计日志
     */
    public Page<AuditLog> queryAuditLogs(AuditLogQueryRequest request) {
        try {
            // 构建分页和排序
            Sort sort = Sort.by(Sort.Direction.fromString(request.getSortDirection()), request.getSortBy());
            Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
            
            // 如果有关键字搜索，使用全文搜索
            if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                return auditLogRepository.searchByKeyword(request.getKeyword().trim(), pageable);
            }
            
            // 使用复合条件查询
            return auditLogRepository.findByComplexConditions(
                request.getUserId(),
                request.getOperationType(),
                request.getResourceType(),
                request.getResourceId(),
                request.getStartTime(),
                request.getEndTime(),
                request.getRiskLevel(),
                request.getIsSensitive(),
                request.getTenantId(),
                pageable
            );
            
        } catch (Exception e) {
            logger.error("查询审计日志失败", e);
            throw new RuntimeException("查询审计日志失败", e);
        }
    }
    
    /**
     * 获取审计日志统计信息
     */
    public AuditLogStatisticsResult getAuditStatistics(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        try {
            AuditLogStatisticsResult result = new AuditLogStatisticsResult(startTime, endTime, 0);
            
            // 总操作数
            long totalOperations = auditLogRepository.countByTimestampBetween(startTime, endTime);
            result.setTotalOperations(totalOperations);
            
            // 按操作类型统计
            List<Object[]> operationTypeStats = auditLogRepository.countByOperationTypeAndTimestampBetween(startTime, endTime);
            Map<String, Long> operationTypeMap = operationTypeStats.stream()
                .collect(Collectors.toMap(
                    row -> (String) row[0],
                    row -> (Long) row[1]
                ));
            result.setOperationTypeStats(operationTypeMap);
            
            // 按用户统计
            List<Object[]> userStats = auditLogRepository.countByUserIdAndTimestampBetween(startTime, endTime);
            Map<String, Long> userMap = userStats.stream()
                .collect(Collectors.toMap(
                    row -> (String) row[0],
                    row -> (Long) row[1]
                ));
            result.setUserStats(userMap);
            
            // 按风险等级统计
            List<Object[]> riskLevelStats = auditLogRepository.countByRiskLevelAndTimestampBetween(startTime, endTime);
            Map<String, Long> riskLevelMap = riskLevelStats.stream()
                .collect(Collectors.toMap(
                    row -> (String) row[0],
                    row -> (Long) row[1]
                ));
            result.setRiskLevelStats(riskLevelMap);
            
            // 异常活跃的IP地址
            List<Object[]> activeIps = auditLogRepository.findActiveIpAddresses(startTime, endTime, 100);
            List<AuditLogStatisticsResult.IpActivityInfo> ipActivityList = activeIps.stream()
                .map(row -> new AuditLogStatisticsResult.IpActivityInfo((String) row[0], (Long) row[1]))
                .collect(Collectors.toList());
            result.setActiveIpAddresses(ipActivityList);
            
            // 计算成功率
            Pageable pageable = PageRequest.of(0, 1);
            Page<AuditLog> failedOps = auditLogRepository.findFailedOperations(startTime, endTime, pageable);
            long failedOperations = failedOps.getTotalElements();
            long successfulOperations = totalOperations - failedOperations;
            
            result.setSuccessfulOperations(successfulOperations);
            result.setFailedOperations(failedOperations);
            
            logger.info("审计统计查询成功: startTime={}, endTime={}, totalOperations={}", 
                       startTime, endTime, totalOperations);
            
            return result;
            
        } catch (Exception e) {
            logger.error("获取审计统计信息失败: startTime={}, endTime={}", startTime, endTime, e);
            throw new RuntimeException("获取审计统计信息失败", e);
        }
    }
    
    /**
     * 根据会话ID获取用户操作轨迹
     */
    public List<AuditLog> getUserOperationTrace(String sessionId) {
        try {
            return auditLogRepository.findBySessionIdOrderByTimestampDesc(sessionId);
        } catch (Exception e) {
            logger.error("获取用户操作轨迹失败: sessionId={}", sessionId, e);
            throw new RuntimeException("获取用户操作轨迹失败", e);
        }
    }
    
    /**
     * 根据请求ID获取关联操作
     */
    public List<AuditLog> getRelatedOperations(String requestId) {
        try {
            return auditLogRepository.findByRequestIdOrderByTimestampDesc(requestId);
        } catch (Exception e) {
            logger.error("获取关联操作失败: requestId={}", requestId, e);
            throw new RuntimeException("获取关联操作失败", e);
        }
    }
    
    /**
     * 清理过期的审计日志
     */
    @Transactional
    public void cleanupExpiredAuditLogs(int retentionDays) {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
            auditLogRepository.deleteByTimestampBefore(cutoffTime);
            
            logger.info("清理过期审计日志完成: cutoffTime={}", cutoffTime);
            
        } catch (Exception e) {
            logger.error("清理过期审计日志失败: retentionDays={}", retentionDays, e);
            throw new RuntimeException("清理过期审计日志失败", e);
        }
    }
    
    /**
     * 数据脱敏处理
     */
    private String maskSensitiveData(Object data) {
        try {
            if (data == null) {
                return null;
            }
            
            // 转换为JSON字符串
            String jsonString = objectMapper.writeValueAsString(data);
            
            // 解析为Map进行脱敏
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = objectMapper.readValue(jsonString, Map.class);
            
            if (dataMap != null) {
                maskSensitiveFields(dataMap);
            }
            
            return objectMapper.writeValueAsString(dataMap);
            
        } catch (JsonProcessingException e) {
            logger.warn("数据脱敏处理失败，返回原始数据", e);
            return data.toString();
        }
    }
    
    /**
     * 递归脱敏敏感字段
     */
    @SuppressWarnings("unchecked")
    private void maskSensitiveFields(Map<String, Object> dataMap) {
        if (dataMap == null) {
            return;
        }
        
        for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
            String key = entry.getKey().toLowerCase();
            Object value = entry.getValue();
            
            // 检查是否为敏感字段
            boolean isSensitive = SENSITIVE_FIELDS.stream().anyMatch(key::contains);
            
            if (isSensitive && value != null) {
                // 脱敏处理
                String maskedValue = maskValue(value.toString());
                entry.setValue(maskedValue);
            } else if (value instanceof Map) {
                // 递归处理嵌套对象
                maskSensitiveFields((Map<String, Object>) value);
            } else if (value instanceof List) {
                // 处理列表
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        maskSensitiveFields((Map<String, Object>) item);
                    }
                }
            }
        }
    }
    
    /**
     * 值脱敏处理
     */
    private String maskValue(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        
        // 保留前2位和后2位，中间用*替换
        int length = value.length();
        StringBuilder masked = new StringBuilder();
        masked.append(value.substring(0, 2));
        for (int i = 2; i < length - 2; i++) {
            masked.append("*");
        }
        masked.append(value.substring(length - 2));
        
        return masked.toString();
    }
    
    /**
     * 判断是否为敏感操作
     */
    private boolean isSensitiveOperation(AuditOperationType operationType, AuditResourceType resourceType) {
        // 删除操作都是敏感的
        if (operationType.name().contains("DELETE")) {
            return true;
        }
        
        // 权限相关操作是敏感的
        if (operationType == AuditOperationType.ASSIGN_ROLE || 
            operationType == AuditOperationType.REVOKE_ROLE) {
            return true;
        }
        
        // 用户相关操作是敏感的
        if (resourceType == AuditResourceType.USER) {
            return true;
        }
        
        // 数据导出是敏感的
        if (operationType == AuditOperationType.EXPORT_DATA) {
            return true;
        }
        
        return false;
    }
}