package com.workflow.component;

import com.workflow.entity.ExceptionRecord;
import com.workflow.entity.ExceptionRecord.ExceptionSeverity;
import com.workflow.entity.ExceptionRecord.ExceptionStatus;
import com.workflow.repository.ExceptionRecordRepository;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 自动重试和补偿机制组件
 * 需求: 9.6, 9.7, 9.8
 * 
 * 功能:
 * - 异常的自动重试机制和指数退避策略
 * - 死信队列处理避免无限重试
 * - 流程的补偿事务和回滚机制
 */
@Component
public class RetryAndCompensationComponent {
    
    private static final Logger log = LoggerFactory.getLogger(RetryAndCompensationComponent.class);
    
    @Autowired
    private ExceptionRecordRepository exceptionRecordRepository;
    
    @Autowired(required = false)
    private RuntimeService runtimeService;
    
    @Autowired(required = false)
    private ExceptionHandlerComponent exceptionHandler;
    
    @Autowired(required = false)
    private NotificationManagerComponent notificationManager;
    
    // 死信队列
    private final Queue<DeadLetterMessage> deadLetterQueue = new ConcurrentLinkedQueue<>();
    
    // 补偿事务注册表
    private final Map<String, CompensationTransaction> compensationRegistry = new ConcurrentHashMap<>();
    
    // 重试配置
    @Value("${workflow.retry.max-attempts:3}")
    private int maxRetryAttempts = 3;
    
    @Value("${workflow.retry.base-delay-seconds:30}")
    private int baseDelaySeconds = 30;
    
    @Value("${workflow.retry.max-delay-minutes:60}")
    private int maxDelayMinutes = 60;
    
    @Value("${workflow.deadletter.retention-days:30}")
    private int deadLetterRetentionDays = 30;

    /**
     * 死信消息类
     */
    public static class DeadLetterMessage {
        private final String id;
        private final String exceptionRecordId;
        private final String processInstanceId;
        private final String taskId;
        private final String reason;
        private final LocalDateTime createdTime;
        private final int totalRetryAttempts;
        private boolean processed;
        private LocalDateTime processedTime;
        private String processedBy;
        private String processedNote;
        
        public DeadLetterMessage(String exceptionRecordId, String processInstanceId, 
                String taskId, String reason, int totalRetryAttempts) {
            this.id = UUID.randomUUID().toString();
            this.exceptionRecordId = exceptionRecordId;
            this.processInstanceId = processInstanceId;
            this.taskId = taskId;
            this.reason = reason;
            this.totalRetryAttempts = totalRetryAttempts;
            this.createdTime = LocalDateTime.now();
            this.processed = false;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public String getExceptionRecordId() { return exceptionRecordId; }
        public String getProcessInstanceId() { return processInstanceId; }
        public String getTaskId() { return taskId; }
        public String getReason() { return reason; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public int getTotalRetryAttempts() { return totalRetryAttempts; }
        public boolean isProcessed() { return processed; }
        public void setProcessed(boolean processed) { this.processed = processed; }
        public LocalDateTime getProcessedTime() { return processedTime; }
        public void setProcessedTime(LocalDateTime processedTime) { this.processedTime = processedTime; }
        public String getProcessedBy() { return processedBy; }
        public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }
        public String getProcessedNote() { return processedNote; }
        public void setProcessedNote(String processedNote) { this.processedNote = processedNote; }
    }

    /**
     * 补偿事务类
     */
    public static class CompensationTransaction {
        private final String id;
        private final String processInstanceId;
        private final String activityId;
        private final String compensationType;
        private final Map<String, Object> compensationData;
        private final LocalDateTime registeredTime;
        private boolean executed;
        private LocalDateTime executedTime;
        private boolean success;
        private String errorMessage;
        
        public CompensationTransaction(String processInstanceId, String activityId, 
                String compensationType, Map<String, Object> compensationData) {
            this.id = UUID.randomUUID().toString();
            this.processInstanceId = processInstanceId;
            this.activityId = activityId;
            this.compensationType = compensationType;
            this.compensationData = compensationData != null ? compensationData : new HashMap<>();
            this.registeredTime = LocalDateTime.now();
            this.executed = false;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public String getProcessInstanceId() { return processInstanceId; }
        public String getActivityId() { return activityId; }
        public String getCompensationType() { return compensationType; }
        public Map<String, Object> getCompensationData() { return compensationData; }
        public LocalDateTime getRegisteredTime() { return registeredTime; }
        public boolean isExecuted() { return executed; }
        public void setExecuted(boolean executed) { this.executed = executed; }
        public LocalDateTime getExecutedTime() { return executedTime; }
        public void setExecutedTime(LocalDateTime executedTime) { this.executedTime = executedTime; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    /**
     * 重试结果类
     */
    public static class RetryResult {
        private final String exceptionRecordId;
        private final boolean success;
        private final int attemptNumber;
        private final String message;
        private final LocalDateTime nextRetryTime;
        private final boolean movedToDeadLetter;
        
        public RetryResult(String exceptionRecordId, boolean success, int attemptNumber, 
                String message, LocalDateTime nextRetryTime, boolean movedToDeadLetter) {
            this.exceptionRecordId = exceptionRecordId;
            this.success = success;
            this.attemptNumber = attemptNumber;
            this.message = message;
            this.nextRetryTime = nextRetryTime;
            this.movedToDeadLetter = movedToDeadLetter;
        }
        
        // Getters
        public String getExceptionRecordId() { return exceptionRecordId; }
        public boolean isSuccess() { return success; }
        public int getAttemptNumber() { return attemptNumber; }
        public String getMessage() { return message; }
        public LocalDateTime getNextRetryTime() { return nextRetryTime; }
        public boolean isMovedToDeadLetter() { return movedToDeadLetter; }
    }


    /**
     * 执行自动重试
     * 需求 9.6: 自动重试机制
     */
    @Transactional
    public RetryResult executeRetry(String exceptionRecordId) {
        log.info("执行自动重试: exceptionRecordId={}", exceptionRecordId);
        
        try {
            ExceptionRecord record = exceptionRecordRepository.findById(exceptionRecordId)
                    .orElseThrow(() -> new RuntimeException("异常记录不存在: " + exceptionRecordId));
            
            // 检查是否已解决
            if (record.getResolved()) {
                return new RetryResult(exceptionRecordId, true, record.getRetryCount(), 
                        "异常已解决，无需重试", null, false);
            }
            
            // 检查是否超过最大重试次数
            if (record.getRetryCount() >= record.getMaxRetryCount()) {
                // 移入死信队列
                moveToDeadLetterQueue(record, "超过最大重试次数");
                return new RetryResult(exceptionRecordId, false, record.getRetryCount(),
                        "超过最大重试次数，已移入死信队列", null, true);
            }
            
            // 更新重试状态
            record.setStatus(ExceptionStatus.PROCESSING);
            record.setRetryCount(record.getRetryCount() + 1);
            record.setLastRetryTime(LocalDateTime.now());
            
            // 尝试执行重试逻辑
            boolean retrySuccess = attemptRetryExecution(record);
            
            if (retrySuccess) {
                // 重试成功
                record.setResolved(true);
                record.setResolvedTime(LocalDateTime.now());
                record.setResolutionMethod("AUTO_RETRY");
                record.setResolutionNote(String.format("第%d次重试成功", record.getRetryCount()));
                record.setStatus(ExceptionStatus.RESOLVED);
                exceptionRecordRepository.save(record);
                
                return new RetryResult(exceptionRecordId, true, record.getRetryCount(),
                        "重试成功", null, false);
            } else {
                // 重试失败，计算下次重试时间
                LocalDateTime nextRetryTime = calculateNextRetryTime(record.getRetryCount());
                record.setNextRetryTime(nextRetryTime);
                record.setStatus(ExceptionStatus.PENDING);
                exceptionRecordRepository.save(record);
                
                // 检查是否达到最大重试次数
                if (record.getRetryCount() >= record.getMaxRetryCount()) {
                    moveToDeadLetterQueue(record, "重试失败且达到最大重试次数");
                    return new RetryResult(exceptionRecordId, false, record.getRetryCount(),
                            "重试失败，已移入死信队列", null, true);
                }
                
                return new RetryResult(exceptionRecordId, false, record.getRetryCount(),
                        String.format("第%d次重试失败，下次重试时间: %s", record.getRetryCount(), nextRetryTime),
                        nextRetryTime, false);
            }
            
        } catch (Exception e) {
            log.error("执行重试失败: {}", e.getMessage(), e);
            throw new RuntimeException("执行重试失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量执行待重试的异常
     */
    @Transactional
    public List<RetryResult> executePendingRetries() {
        log.info("执行待重试的异常");
        
        List<RetryResult> results = new ArrayList<>();
        
        try {
            List<ExceptionRecord> pendingRetries = exceptionRecordRepository
                    .findPendingRetryExceptions(LocalDateTime.now());
            
            for (ExceptionRecord record : pendingRetries) {
                try {
                    RetryResult result = executeRetry(record.getId());
                    results.add(result);
                } catch (Exception e) {
                    log.error("重试异常失败: id={}, error={}", record.getId(), e.getMessage());
                    results.add(new RetryResult(record.getId(), false, record.getRetryCount(),
                            "重试执行异常: " + e.getMessage(), null, false));
                }
            }
            
            log.info("批量重试完成: 处理了{}个异常", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("批量执行重试失败: {}", e.getMessage(), e);
            throw new RuntimeException("批量执行重试失败: " + e.getMessage(), e);
        }
    }

    /**
     * 定时执行待重试任务
     */
    @Scheduled(fixedDelayString = "${workflow.retry.schedule-delay:60000}")
    public void scheduledRetryExecution() {
        log.debug("定时执行待重试任务");
        try {
            executePendingRetries();
        } catch (Exception e) {
            log.error("定时重试执行失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 移入死信队列
     * 需求 9.7: 死信队列处理
     */
    @Transactional
    public DeadLetterMessage moveToDeadLetterQueue(ExceptionRecord record, String reason) {
        log.info("移入死信队列: exceptionRecordId={}, reason={}", record.getId(), reason);
        
        try {
            DeadLetterMessage message = new DeadLetterMessage(
                    record.getId(),
                    record.getProcessInstanceId(),
                    record.getTaskId(),
                    reason,
                    record.getRetryCount()
            );
            
            deadLetterQueue.add(message);
            
            // 更新异常记录状态
            record.setStatus(ExceptionStatus.PENDING);
            record.setResolutionNote("已移入死信队列: " + reason);
            exceptionRecordRepository.save(record);
            
            // 发送告警通知
            sendDeadLetterAlert(message);
            
            log.info("已移入死信队列: messageId={}", message.getId());
            return message;
            
        } catch (Exception e) {
            log.error("移入死信队列失败: {}", e.getMessage(), e);
            throw new RuntimeException("移入死信队列失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理死信消息
     */
    @Transactional
    public Map<String, Object> processDeadLetterMessage(String messageId, String processedBy, 
            String action, String note) {
        log.info("处理死信消息: messageId={}, action={}", messageId, action);
        
        Map<String, Object> result = new HashMap<>();
        result.put("messageId", messageId);
        result.put("processedTime", LocalDateTime.now());
        
        try {
            DeadLetterMessage message = deadLetterQueue.stream()
                    .filter(m -> messageId.equals(m.getId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("死信消息不存在: " + messageId));
            
            if (message.isProcessed()) {
                result.put("success", false);
                result.put("message", "消息已被处理");
                return result;
            }
            
            switch (action.toUpperCase()) {
                case "RETRY":
                    // 重置重试计数并重新尝试
                    ExceptionRecord record = exceptionRecordRepository.findById(message.getExceptionRecordId())
                            .orElse(null);
                    if (record != null) {
                        record.setRetryCount(0);
                        record.setNextRetryTime(LocalDateTime.now());
                        record.setStatus(ExceptionStatus.PENDING);
                        exceptionRecordRepository.save(record);
                    }
                    result.put("action", "RETRY");
                    break;
                    
                case "IGNORE":
                    // 忽略该异常
                    if (exceptionHandler != null) {
                        exceptionHandler.ignoreException(message.getExceptionRecordId(), processedBy, note);
                    }
                    result.put("action", "IGNORE");
                    break;
                    
                case "COMPENSATE":
                    // 执行补偿
                    executeCompensation(message.getProcessInstanceId());
                    result.put("action", "COMPENSATE");
                    break;
                    
                default:
                    result.put("success", false);
                    result.put("message", "未知操作: " + action);
                    return result;
            }
            
            message.setProcessed(true);
            message.setProcessedTime(LocalDateTime.now());
            message.setProcessedBy(processedBy);
            message.setProcessedNote(note);
            
            result.put("success", true);
            result.put("message", "处理成功");
            return result;
            
        } catch (Exception e) {
            log.error("处理死信消息失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 获取死信队列消息列表
     */
    public List<Map<String, Object>> getDeadLetterMessages(boolean includeProcessed) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        for (DeadLetterMessage message : deadLetterQueue) {
            if (includeProcessed || !message.isProcessed()) {
                Map<String, Object> msgInfo = new HashMap<>();
                msgInfo.put("id", message.getId());
                msgInfo.put("exceptionRecordId", message.getExceptionRecordId());
                msgInfo.put("processInstanceId", message.getProcessInstanceId());
                msgInfo.put("taskId", message.getTaskId());
                msgInfo.put("reason", message.getReason());
                msgInfo.put("totalRetryAttempts", message.getTotalRetryAttempts());
                msgInfo.put("createdTime", message.getCreatedTime());
                msgInfo.put("processed", message.isProcessed());
                msgInfo.put("processedTime", message.getProcessedTime());
                msgInfo.put("processedBy", message.getProcessedBy());
                messages.add(msgInfo);
            }
        }
        
        return messages;
    }


    /**
     * 注册补偿事务
     * 需求 9.8: 补偿事务机制
     */
    @Transactional
    public CompensationTransaction registerCompensation(String processInstanceId, String activityId,
            String compensationType, Map<String, Object> compensationData) {
        log.info("注册补偿事务: processInstanceId={}, activityId={}, type={}", 
                processInstanceId, activityId, compensationType);
        
        try {
            CompensationTransaction transaction = new CompensationTransaction(
                    processInstanceId, activityId, compensationType, compensationData);
            
            String key = processInstanceId + ":" + activityId;
            compensationRegistry.put(key, transaction);
            
            log.info("补偿事务已注册: id={}", transaction.getId());
            return transaction;
            
        } catch (Exception e) {
            log.error("注册补偿事务失败: {}", e.getMessage(), e);
            throw new RuntimeException("注册补偿事务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行补偿事务
     */
    @Transactional
    public List<Map<String, Object>> executeCompensation(String processInstanceId) {
        log.info("执行补偿事务: processInstanceId={}", processInstanceId);
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        try {
            // 获取该流程实例的所有补偿事务（按注册时间倒序执行）
            List<CompensationTransaction> transactions = compensationRegistry.values().stream()
                    .filter(t -> processInstanceId.equals(t.getProcessInstanceId()))
                    .filter(t -> !t.isExecuted())
                    .sorted((a, b) -> b.getRegisteredTime().compareTo(a.getRegisteredTime()))
                    .toList();
            
            for (CompensationTransaction transaction : transactions) {
                Map<String, Object> result = executeCompensationTransaction(transaction);
                results.add(result);
            }
            
            log.info("补偿事务执行完成: 处理了{}个事务", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("执行补偿事务失败: {}", e.getMessage(), e);
            throw new RuntimeException("执行补偿事务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行单个补偿事务
     */
    private Map<String, Object> executeCompensationTransaction(CompensationTransaction transaction) {
        log.info("执行补偿事务: id={}, type={}", transaction.getId(), transaction.getCompensationType());
        
        Map<String, Object> result = new HashMap<>();
        result.put("transactionId", transaction.getId());
        result.put("activityId", transaction.getActivityId());
        result.put("compensationType", transaction.getCompensationType());
        result.put("executedTime", LocalDateTime.now());
        
        try {
            // 根据补偿类型执行不同的补偿逻辑
            switch (transaction.getCompensationType().toUpperCase()) {
                case "ROLLBACK_VARIABLES":
                    executeVariableRollback(transaction);
                    break;
                    
                case "CANCEL_TASK":
                    executeCancelTask(transaction);
                    break;
                    
                case "TERMINATE_PROCESS":
                    executeTerminateProcess(transaction);
                    break;
                    
                case "CUSTOM":
                    executeCustomCompensation(transaction);
                    break;
                    
                default:
                    log.warn("未知的补偿类型: {}", transaction.getCompensationType());
            }
            
            transaction.setExecuted(true);
            transaction.setExecutedTime(LocalDateTime.now());
            transaction.setSuccess(true);
            
            result.put("success", true);
            result.put("message", "补偿执行成功");
            
        } catch (Exception e) {
            log.error("执行补偿事务失败: {}", e.getMessage(), e);
            
            transaction.setExecuted(true);
            transaction.setExecutedTime(LocalDateTime.now());
            transaction.setSuccess(false);
            transaction.setErrorMessage(e.getMessage());
            
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * 执行变量回滚补偿
     */
    private void executeVariableRollback(CompensationTransaction transaction) {
        log.info("执行变量回滚: processInstanceId={}", transaction.getProcessInstanceId());
        
        if (runtimeService == null) {
            log.warn("RuntimeService不可用，跳过变量回滚");
            return;
        }
        
        Map<String, Object> originalValues = transaction.getCompensationData();
        if (originalValues != null && !originalValues.isEmpty()) {
            try {
                ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(transaction.getProcessInstanceId())
                        .singleResult();
                
                if (instance != null) {
                    runtimeService.setVariables(transaction.getProcessInstanceId(), originalValues);
                    log.info("变量已回滚: {} 个变量", originalValues.size());
                }
            } catch (Exception e) {
                log.error("变量回滚失败: {}", e.getMessage());
                throw e;
            }
        }
    }

    /**
     * 执行取消任务补偿
     */
    private void executeCancelTask(CompensationTransaction transaction) {
        log.info("执行取消任务: activityId={}", transaction.getActivityId());
        
        // 任务取消逻辑（简化实现）
        log.info("任务取消补偿已执行");
    }

    /**
     * 执行终止流程补偿
     */
    private void executeTerminateProcess(CompensationTransaction transaction) {
        log.info("执行终止流程: processInstanceId={}", transaction.getProcessInstanceId());
        
        if (runtimeService == null) {
            log.warn("RuntimeService不可用，跳过流程终止");
            return;
        }
        
        try {
            ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(transaction.getProcessInstanceId())
                    .singleResult();
            
            if (instance != null) {
                String reason = (String) transaction.getCompensationData().getOrDefault("reason", "补偿终止");
                runtimeService.deleteProcessInstance(transaction.getProcessInstanceId(), reason);
                log.info("流程已终止: {}", transaction.getProcessInstanceId());
            }
        } catch (Exception e) {
            log.error("终止流程失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 执行自定义补偿
     */
    private void executeCustomCompensation(CompensationTransaction transaction) {
        log.info("执行自定义补偿: activityId={}", transaction.getActivityId());
        
        // 自定义补偿逻辑（可以通过回调或事件扩展）
        Map<String, Object> data = transaction.getCompensationData();
        if (data.containsKey("callback")) {
            log.info("执行自定义补偿回调: {}", data.get("callback"));
        }
        
        log.info("自定义补偿已执行");
    }

    /**
     * 获取补偿事务列表
     */
    public List<Map<String, Object>> getCompensationTransactions(String processInstanceId, 
            boolean includeExecuted) {
        List<Map<String, Object>> transactions = new ArrayList<>();
        
        for (CompensationTransaction transaction : compensationRegistry.values()) {
            if (processInstanceId == null || processInstanceId.equals(transaction.getProcessInstanceId())) {
                if (includeExecuted || !transaction.isExecuted()) {
                    Map<String, Object> txInfo = new HashMap<>();
                    txInfo.put("id", transaction.getId());
                    txInfo.put("processInstanceId", transaction.getProcessInstanceId());
                    txInfo.put("activityId", transaction.getActivityId());
                    txInfo.put("compensationType", transaction.getCompensationType());
                    txInfo.put("registeredTime", transaction.getRegisteredTime());
                    txInfo.put("executed", transaction.isExecuted());
                    txInfo.put("executedTime", transaction.getExecutedTime());
                    txInfo.put("success", transaction.isSuccess());
                    txInfo.put("errorMessage", transaction.getErrorMessage());
                    transactions.add(txInfo);
                }
            }
        }
        
        return transactions;
    }

    // ==================== 辅助方法 ====================

    /**
     * 尝试执行重试逻辑
     */
    private boolean attemptRetryExecution(ExceptionRecord record) {
        log.info("尝试重试执行: processInstanceId={}, taskId={}", 
                record.getProcessInstanceId(), record.getTaskId());
        
        try {
            // 检查流程实例是否仍然存在
            if (runtimeService != null && record.getProcessInstanceId() != null) {
                ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(record.getProcessInstanceId())
                        .singleResult();
                
                if (instance == null) {
                    // 流程实例已不存在，视为成功（无需重试）
                    log.info("流程实例已不存在，标记为成功");
                    return true;
                }
                
                // 如果流程被挂起，尝试激活
                if (instance.isSuspended()) {
                    runtimeService.activateProcessInstanceById(record.getProcessInstanceId());
                    log.info("流程实例已激活");
                }
                
                // 尝试触发流程继续执行
                List<Execution> executions = runtimeService.createExecutionQuery()
                        .processInstanceId(record.getProcessInstanceId())
                        .list();
                
                if (!executions.isEmpty()) {
                    // 流程有活跃的执行，视为重试成功
                    log.info("流程有活跃执行，重试成功");
                    return true;
                }
            }
            
            // 默认返回false，表示需要继续重试
            return false;
            
        } catch (Exception e) {
            log.error("重试执行失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 计算下次重试时间（指数退避）
     */
    private LocalDateTime calculateNextRetryTime(int retryCount) {
        long delaySeconds = (long) (baseDelaySeconds * Math.pow(2, retryCount));
        long maxDelaySeconds = maxDelayMinutes * 60L;
        delaySeconds = Math.min(delaySeconds, maxDelaySeconds);
        
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }

    /**
     * 发送死信队列告警
     */
    private void sendDeadLetterAlert(DeadLetterMessage message) {
        log.info("发送死信队列告警: messageId={}", message.getId());
        
        if (notificationManager != null) {
            try {
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("messageId", message.getId());
                eventData.put("exceptionRecordId", message.getExceptionRecordId());
                eventData.put("processInstanceId", message.getProcessInstanceId());
                eventData.put("reason", message.getReason());
                eventData.put("totalRetryAttempts", message.getTotalRetryAttempts());
                
                log.info("死信队列告警事件: {}", eventData);
            } catch (Exception e) {
                log.error("发送死信队列告警失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 清理过期的死信消息
     */
    @Scheduled(cron = "${workflow.deadletter.cleanup-cron:0 0 2 * * ?}")
    public void cleanupExpiredDeadLetterMessages() {
        log.info("清理过期的死信消息");
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(deadLetterRetentionDays);
        
        int removed = 0;
        Iterator<DeadLetterMessage> iterator = deadLetterQueue.iterator();
        while (iterator.hasNext()) {
            DeadLetterMessage message = iterator.next();
            if (message.isProcessed() && message.getProcessedTime() != null &&
                message.getProcessedTime().isBefore(cutoffTime)) {
                iterator.remove();
                removed++;
            }
        }
        
        log.info("已清理{}条过期死信消息", removed);
    }

    /**
     * 获取重试统计信息
     */
    public Map<String, Object> getRetryStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 死信队列统计
        long totalDeadLetters = deadLetterQueue.size();
        long processedDeadLetters = deadLetterQueue.stream().filter(DeadLetterMessage::isProcessed).count();
        long pendingDeadLetters = totalDeadLetters - processedDeadLetters;
        
        stats.put("deadLetterTotal", totalDeadLetters);
        stats.put("deadLetterProcessed", processedDeadLetters);
        stats.put("deadLetterPending", pendingDeadLetters);
        
        // 补偿事务统计
        long totalCompensations = compensationRegistry.size();
        long executedCompensations = compensationRegistry.values().stream()
                .filter(CompensationTransaction::isExecuted).count();
        long successfulCompensations = compensationRegistry.values().stream()
                .filter(t -> t.isExecuted() && t.isSuccess()).count();
        
        stats.put("compensationTotal", totalCompensations);
        stats.put("compensationExecuted", executedCompensations);
        stats.put("compensationSuccessful", successfulCompensations);
        
        // 待重试异常统计
        List<ExceptionRecord> pendingRetries = exceptionRecordRepository
                .findPendingRetryExceptions(LocalDateTime.now());
        stats.put("pendingRetries", pendingRetries.size());
        
        return stats;
    }
}
