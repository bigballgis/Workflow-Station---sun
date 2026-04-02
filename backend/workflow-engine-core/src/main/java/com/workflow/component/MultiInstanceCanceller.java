package com.workflow.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.MultiInstanceCancelResult;
import com.workflow.entity.AuditLog;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.repository.AuditLogRepository;
import com.workflow.repository.ExtendedTaskInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 多实例取消器
 * 
 * 负责主流程取消/撤回时的级联处理，自动终止多实例子流程中所有未完成的子任务。
 * 
 * 核心职责：
 * 1. 查询流程实例中所有活跃的多实例子流程执行
 * 2. 批量更新对应的 ExtendedTaskInfo 状态为 CANCELLED
 * 3. 记录审计日志（被取消数量、各子任务处理人、取消前状态）
 * 4. 部分更新失败时记录 ERROR 日志，继续处理其他子任务
 * 5. 无活跃子任务时静默跳过
 * 
 * 设计决策：
 * - 不回滚子表数据（保留已提交的数据）
 * - 使用事务确保状态更新的一致性
 * - 失败时不影响主流程的取消操作
 */
@Slf4j
@Component
public class MultiInstanceCanceller {
    
    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 取消多实例子流程中所有未完成的子任务
     * 
     * @param processInstanceId 主流程实例 ID
     * @return 被取消的子任务数量和详情
     */
    @Transactional
    public MultiInstanceCancelResult cancelMultiInstanceTasks(String processInstanceId) {
        log.info("开始取消多实例子任务: processInstanceId={}", processInstanceId);
        
        try {
            // 1. 查询流程实例中所有活跃的子流程执行
            List<Execution> activeExecutions = queryActiveMultiInstanceExecutions(processInstanceId);
            
            if (activeExecutions.isEmpty()) {
                log.debug("流程实例 {} 中没有活跃的多实例子任务，静默跳过", processInstanceId);
                return MultiInstanceCancelResult.builder()
                    .cancelledCount(0)
                    .failedCount(0)
                    .cancelledTasks(new ArrayList<>())
                    .build();
            }
            
            log.debug("找到 {} 个活跃的多实例子流程执行", activeExecutions.size());
            
            // 2. 查询流程实例中所有未完成的扩展任务信息
            List<ExtendedTaskInfo> activeTasks = extendedTaskInfoRepository
                .findByProcessInstanceIdAndIsDeletedFalse(processInstanceId);
            
            // 过滤出多实例子任务（通过 extendedProperties 中的 multiInstance 标记）
            List<ExtendedTaskInfo> multiInstanceTasks = filterMultiInstanceTasks(activeTasks);
            
            if (multiInstanceTasks.isEmpty()) {
                log.debug("流程实例 {} 中没有多实例子任务的 ExtendedTaskInfo 记录", processInstanceId);
                return MultiInstanceCancelResult.builder()
                    .cancelledCount(0)
                    .failedCount(0)
                    .cancelledTasks(new ArrayList<>())
                    .build();
            }
            
            log.debug("找到 {} 个多实例子任务的 ExtendedTaskInfo 记录", multiInstanceTasks.size());
            
            // 3. 批量更新 ExtendedTaskInfo 状态为 CANCELLED
            MultiInstanceCancelResult result = cancelTasks(multiInstanceTasks);
            
            // 4. 记录审计日志
            recordAuditLog(processInstanceId, result);
            
            log.info("多实例子任务取消完成: processInstanceId={}, cancelledCount={}, failedCount={}", 
                processInstanceId, result.getCancelledCount(), result.getFailedCount());
            
            return result;
            
        } catch (Exception e) {
            log.error("取消多实例子任务时发生异常: processInstanceId={}", processInstanceId, e);
            // 不抛出异常，避免影响主流程的取消操作
            return MultiInstanceCancelResult.builder()
                .cancelledCount(0)
                .failedCount(0)
                .cancelledTasks(new ArrayList<>())
                .build();
        }
    }
    
    /**
     * 查询流程实例中所有活跃的多实例子流程执行
     */
    private List<Execution> queryActiveMultiInstanceExecutions(String processInstanceId) {
        try {
            // 查询所有活跃的执行（包括子流程执行）
            List<Execution> executions = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .list();
            
            // 过滤出多实例相关的执行
            // 多实例执行的 activityId 通常包含 "MultiInstance" 或者是多实例子流程的 ID
            List<Execution> multiInstanceExecutions = new ArrayList<>();
            for (Execution execution : executions) {
                String activityId = execution.getActivityId();
                if (activityId != null && activityId.contains("MultiInstance")) {
                    multiInstanceExecutions.add(execution);
                }
            }
            
            return multiInstanceExecutions;
        } catch (Exception e) {
            log.error("查询活跃的多实例执行时发生异常: processInstanceId={}", processInstanceId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 过滤出多实例子任务
     */
    private List<ExtendedTaskInfo> filterMultiInstanceTasks(List<ExtendedTaskInfo> tasks) {
        List<ExtendedTaskInfo> multiInstanceTasks = new ArrayList<>();
        
        for (ExtendedTaskInfo task : tasks) {
            // 跳过已完成或已取消的任务
            if ("COMPLETED".equals(task.getStatus()) || "CANCELLED".equals(task.getStatus())) {
                continue;
            }
            
            // 检查 extendedProperties 中是否包含 multiInstance 标记
            if (isMultiInstanceTask(task)) {
                multiInstanceTasks.add(task);
            }
        }
        
        return multiInstanceTasks;
    }
    
    /**
     * 检查任务是否为多实例子任务
     */
    private boolean isMultiInstanceTask(ExtendedTaskInfo task) {
        String extendedProperties = task.getExtendedProperties();
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return false;
        }
        
        try {
            Map<String, Object> properties = objectMapper.readValue(
                extendedProperties, 
                new TypeReference<Map<String, Object>>() {}
            );
            
            Object multiInstance = properties.get("multiInstance");
            return multiInstance != null && Boolean.TRUE.equals(multiInstance);
        } catch (Exception e) {
            log.warn("解析 extendedProperties 失败: taskId={}", task.getTaskId(), e);
            return false;
        }
    }
    
    /**
     * 批量取消任务
     */
    private MultiInstanceCancelResult cancelTasks(List<ExtendedTaskInfo> tasks) {
        List<MultiInstanceCancelResult.CancelledTaskDetail> cancelledTasks = new ArrayList<>();
        int cancelledCount = 0;
        int failedCount = 0;
        
        for (ExtendedTaskInfo task : tasks) {
            try {
                // 记录取消前的状态
                String previousStatus = task.getStatus();
                String assigneeId = task.getCurrentAssignee();
                
                // 解析 extendedProperties 获取子表信息
                Map<String, Object> properties = parseExtendedProperties(task.getExtendedProperties());
                Long subTableRowId = extractLongValue(properties.get("subTableRowId"));
                String subTableName = (String) properties.get("subTableName");
                
                // 更新任务状态为 CANCELLED
                task.updateStatus("CANCELLED", "SYSTEM");
                extendedTaskInfoRepository.save(task);
                
                // 记录取消详情
                cancelledTasks.add(MultiInstanceCancelResult.CancelledTaskDetail.builder()
                    .taskId(task.getTaskId())
                    .assigneeId(assigneeId)
                    .previousStatus(previousStatus)
                    .subTableRowId(subTableRowId)
                    .subTableName(subTableName)
                    .build());
                
                cancelledCount++;
                
                log.debug("成功取消多实例子任务: taskId={}, assigneeId={}, previousStatus={}", 
                    task.getTaskId(), assigneeId, previousStatus);
                
            } catch (Exception e) {
                log.error("取消多实例子任务失败: taskId={}", task.getTaskId(), e);
                failedCount++;
            }
        }
        
        return MultiInstanceCancelResult.builder()
            .cancelledCount(cancelledCount)
            .failedCount(failedCount)
            .cancelledTasks(cancelledTasks)
            .build();
    }
    
    /**
     * 解析 extendedProperties JSON
     */
    private Map<String, Object> parseExtendedProperties(String extendedProperties) {
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return Map.of();
        }
        
        try {
            return objectMapper.readValue(
                extendedProperties, 
                new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            log.warn("解析 extendedProperties 失败", e);
            return Map.of();
        }
    }
    
    /**
     * 提取 Long 值（处理不同的数字类型）
     */
    private Long extractLongValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 记录审计日志
     */
    private void recordAuditLog(String processInstanceId, MultiInstanceCancelResult result) {
        try {
            // 构建审计日志上下文数据
            String contextData = objectMapper.writeValueAsString(Map.of(
                "cancelledCount", result.getCancelledCount(),
                "failedCount", result.getFailedCount(),
                "cancelledTasks", result.getCancelledTasks()
            ));
            
            AuditLog auditLog = new AuditLog();
            auditLog.setId(UUID.randomUUID().toString());
            auditLog.setUserId("SYSTEM");
            auditLog.setOperationType("CANCEL");
            auditLog.setResourceType("MULTI_INSTANCE_TASKS");
            auditLog.setResourceId(processInstanceId);
            auditLog.setResourceName("多实例子任务");
            auditLog.setOperationDescription(String.format(
                "取消流程实例 %s 中的多实例子任务，共取消 %d 个任务，失败 %d 个",
                processInstanceId, result.getCancelledCount(), result.getFailedCount()
            ));
            auditLog.setOperationResult(result.getFailedCount() == 0 ? "SUCCESS" : "PARTIAL");
            auditLog.setTimestamp(LocalDateTime.now());
            auditLog.setContextData(contextData);
            auditLog.setRiskLevel("MEDIUM");
            auditLog.setIsSensitive(false);
            
            auditLogRepository.save(auditLog);
            
            log.debug("审计日志记录成功: processInstanceId={}", processInstanceId);
            
        } catch (Exception e) {
            log.error("记录审计日志失败: processInstanceId={}", processInstanceId, e);
            // 不抛出异常，避免影响取消操作
        }
    }
}
