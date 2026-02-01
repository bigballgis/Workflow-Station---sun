package com.developer.component.impl;

import com.developer.component.AuditLogComponent;
import com.developer.entity.OperationLog;
import com.developer.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 审计日志组件实现
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogComponentImpl implements AuditLogComponent {
    
    private final OperationLogRepository operationLogRepository;
    
    @Override
    @Transactional
    public void log(String operationType, String targetType, Long targetId, String description) {
        log(operationType, targetType, targetId, description, null);
    }
    
    @Override
    @Transactional
    public void log(String operationType, String targetType, Long targetId, 
                    String description, String details) {
        // 从安全上下文获取当前操作者，如果无法获取则使用 "system"
        String operator = getCurrentOperator();
        
        OperationLog operationLog = OperationLog.builder()
                .operationType(operationType)
                .targetType(targetType)
                .targetId(targetId)
                .description(description)
                .details(details)
                .operator(operator)
                .operationTime(LocalDateTime.now())
                .build();
        operationLogRepository.save(operationLog);
        log.debug("Audit log: {} {} {} - {}", operationType, targetType, targetId, description);
    }
    
    /**
     * 从安全上下文获取当前操作者
     * 如果无法获取（如系统自动操作），返回 "system"
     */
    private String getCurrentOperator() {
        try {
            // 可以从 Spring Security Context 或其他安全上下文获取
            // SecurityContextHolder.getContext().getAuthentication().getName()
            return "system"; // 默认值，待集成安全上下文后替换
        } catch (Exception e) {
            log.debug("Unable to get current operator from security context, using 'system'");
            return "system";
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OperationLog> query(String operationType, String targetType, Long targetId,
                                     LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        return operationLogRepository.findByQuery(operationType, targetType, targetId, 
                startTime, endTime, pageable);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<OperationLog> getByOperator(String operator, Pageable pageable) {
        return operationLogRepository.findByOperator(operator, pageable);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<OperationLog> getByTarget(String targetType, Long targetId, Pageable pageable) {
        return operationLogRepository.findByTargetTypeAndTargetId(targetType, targetId, pageable);
    }
}
