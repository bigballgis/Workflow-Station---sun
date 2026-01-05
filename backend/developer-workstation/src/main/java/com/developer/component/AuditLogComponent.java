package com.developer.component;

import com.developer.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * 审计日志组件接口
 */
public interface AuditLogComponent {
    
    /**
     * 记录操作日志
     */
    void log(String operationType, String targetType, Long targetId, String description);
    
    /**
     * 记录操作日志（带详情）
     */
    void log(String operationType, String targetType, Long targetId, String description, String details);
    
    /**
     * 查询操作日志
     */
    Page<OperationLog> query(String operationType, String targetType, Long targetId, 
                              LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
    
    /**
     * 获取用户操作日志
     */
    Page<OperationLog> getByOperator(String operator, Pageable pageable);
    
    /**
     * 获取目标对象的操作历史
     */
    Page<OperationLog> getByTarget(String targetType, Long targetId, Pageable pageable);
}
