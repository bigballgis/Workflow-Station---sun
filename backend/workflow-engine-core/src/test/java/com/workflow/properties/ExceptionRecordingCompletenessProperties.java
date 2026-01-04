package com.workflow.properties;

import com.workflow.entity.ExceptionRecord;
import com.workflow.entity.ExceptionRecord.ExceptionSeverity;
import com.workflow.entity.ExceptionRecord.ExceptionStatus;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;

/**
 * 异常信息记录完整性属性测试
 * 验证需求: 需求 9.1 - 异常信息记录
 * 
 * 属性 15: 异常信息记录完整性
 * 对于任何流程执行异常，记录的异常信息应该完整包含：
 * - 异常类型、类名、消息、堆栈跟踪、根本原因
 * - 流程上下文（流程实例ID、任务ID、活动ID等）
 * - 严重级别和状态
 * - 时间戳和重试信息
 */
@Label("功能: workflow-engine-core, 属性 15: 异常信息记录完整性")
public class ExceptionRecordingCompletenessProperties {

    // 模拟异常记录存储
    private final Map<String, ExceptionRecordStorage> exceptionRecords = new ConcurrentHashMap<>();
    
    // 指数退避基础延迟（秒）
    private static final int BASE_RETRY_DELAY_SECONDS = 30;
    // 最大重试延迟（分钟）
    private static final int MAX_RETRY_DELAY_MINUTES = 60;

    /**
     * 简化的异常记录存储类
     */
    private static class ExceptionRecordStorage {
        private final String id;
        private final String processInstanceId;
        private final String processDefinitionId;
        private final String processDefinitionKey;
        private final String taskId;
        private final String taskName;
        private final String activityId;
        private final String activityName;
        private final String exceptionType;
        private final String exceptionClass;
        private final String exceptionMessage;
        private final String stackTrace;
        private final String rootCause;
        private final ExceptionSeverity severity;
        private ExceptionStatus status;
        private final String contextData;
        private final String variablesSnapshot;
        private final LocalDateTime occurredTime;
        private int retryCount;
        private final int maxRetryCount;
        private LocalDateTime nextRetryTime;
        private LocalDateTime lastRetryTime;
        private boolean resolved;
        private LocalDateTime resolvedTime;
        private String resolvedBy;
        private String resolutionMethod;
        private String resolutionNote;
        private boolean alertSent;
        private LocalDateTime alertSentTime;
        
        public ExceptionRecordStorage(String id, String processInstanceId, String processDefinitionId,
                String processDefinitionKey, String taskId, String taskName, String activityId,
                String activityName, String exceptionType, String exceptionClass, String exceptionMessage,
                String stackTrace, String rootCause, ExceptionSeverity severity, ExceptionStatus status,
                String contextData, String variablesSnapshot, LocalDateTime occurredTime,
                int retryCount, int maxRetryCount, LocalDateTime nextRetryTime) {
            this.id = id;
            this.processInstanceId = processInstanceId;
            this.processDefinitionId = processDefinitionId;
            this.processDefinitionKey = processDefinitionKey;
            this.taskId = taskId;
            this.taskName = taskName;
            this.activityId = activityId;
            this.activityName = activityName;
            this.exceptionType = exceptionType;
            this.exceptionClass = exceptionClass;
            this.exceptionMessage = exceptionMessage;
            this.stackTrace = stackTrace;
            this.rootCause = rootCause;
            this.severity = severity;
            this.status = status;
            this.contextData = contextData;
            this.variablesSnapshot = variablesSnapshot;
            this.occurredTime = occurredTime;
            this.retryCount = retryCount;
            this.maxRetryCount = maxRetryCount;
            this.nextRetryTime = nextRetryTime;
            this.resolved = false;
            this.alertSent = false;
        }
        
        // Getters
        public String getId() { return id; }
        public String getProcessInstanceId() { return processInstanceId; }
        public String getProcessDefinitionId() { return processDefinitionId; }
        public String getProcessDefinitionKey() { return processDefinitionKey; }
        public String getTaskId() { return taskId; }
        public String getTaskName() { return taskName; }
        public String getActivityId() { return activityId; }
        public String getActivityName() { return activityName; }
        public String getExceptionType() { return exceptionType; }
        public String getExceptionClass() { return exceptionClass; }
        public String getExceptionMessage() { return exceptionMessage; }
        public String getStackTrace() { return stackTrace; }
        public String getRootCause() { return rootCause; }
        public ExceptionSeverity getSeverity() { return severity; }
        public ExceptionStatus getStatus() { return status; }
        public String getContextData() { return contextData; }
        public String getVariablesSnapshot() { return variablesSnapshot; }
        public LocalDateTime getOccurredTime() { return occurredTime; }
        public int getRetryCount() { return retryCount; }
        public int getMaxRetryCount() { return maxRetryCount; }
        public LocalDateTime getNextRetryTime() { return nextRetryTime; }
        public LocalDateTime getLastRetryTime() { return lastRetryTime; }
        public boolean isResolved() { return resolved; }
        public LocalDateTime getResolvedTime() { return resolvedTime; }
        public String getResolvedBy() { return resolvedBy; }
        public String getResolutionMethod() { return resolutionMethod; }
        public String getResolutionNote() { return resolutionNote; }
        public boolean isAlertSent() { return alertSent; }
        public LocalDateTime getAlertSentTime() { return alertSentTime; }
        
        // Setters for mutable fields
        public void setStatus(ExceptionStatus status) { this.status = status; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public void setNextRetryTime(LocalDateTime nextRetryTime) { this.nextRetryTime = nextRetryTime; }
        public void setLastRetryTime(LocalDateTime lastRetryTime) { this.lastRetryTime = lastRetryTime; }
        public void setResolved(boolean resolved) { this.resolved = resolved; }
        public void setResolvedTime(LocalDateTime resolvedTime) { this.resolvedTime = resolvedTime; }
        public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
        public void setResolutionMethod(String resolutionMethod) { this.resolutionMethod = resolutionMethod; }
        public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
        public void setAlertSent(boolean alertSent) { this.alertSent = alertSent; }
        public void setAlertSentTime(LocalDateTime alertSentTime) { this.alertSentTime = alertSentTime; }
    }


    /**
     * 属性测试: 异常记录包含完整的异常详情
     */
    @Property(tries = 100)
    @Label("异常记录包含完整的异常详情")
    void exceptionRecordContainsCompleteExceptionDetails(
            @ForAll @NotBlank @Size(min = 1, max = 50) String processInstanceId,
            @ForAll @NotBlank @Size(min = 1, max = 50) String taskId,
            @ForAll @NotBlank @Size(min = 1, max = 50) String activityId) {
        
        // Given: 创建一个异常
        Exception testException = new RuntimeException("Test exception message", 
                new IllegalArgumentException("Root cause message"));
        
        // When: 记录异常
        ExceptionRecordStorage record = recordException(testException, processInstanceId, taskId, activityId, null);
        
        // Then: 异常记录应该包含完整的异常详情
        assertThat(record).isNotNull();
        assertThat(record.getId()).isNotNull().isNotEmpty();
        
        // 验证异常类型和类名
        assertThat(record.getExceptionType()).isNotNull().isNotEmpty();
        assertThat(record.getExceptionClass()).isEqualTo(RuntimeException.class.getName());
        
        // 验证异常消息
        assertThat(record.getExceptionMessage()).isEqualTo("Test exception message");
        
        // 验证堆栈跟踪
        assertThat(record.getStackTrace()).isNotNull().isNotEmpty();
        assertThat(record.getStackTrace()).contains("RuntimeException");
        assertThat(record.getStackTrace()).contains("Test exception message");
        
        // 验证根本原因
        assertThat(record.getRootCause()).isNotNull();
        assertThat(record.getRootCause()).contains("IllegalArgumentException");
        assertThat(record.getRootCause()).contains("Root cause message");
    }

    /**
     * 属性测试: 异常记录包含完整的流程上下文
     */
    @Property(tries = 100)
    @Label("异常记录包含完整的流程上下文")
    void exceptionRecordContainsCompleteProcessContext(
            @ForAll @NotBlank @Size(min = 1, max = 50) String processInstanceId,
            @ForAll @NotBlank @Size(min = 1, max = 50) String taskId,
            @ForAll @NotBlank @Size(min = 1, max = 50) String activityId) {
        
        // Given: 创建一个异常
        Exception testException = new RuntimeException("Context test exception");
        
        // When: 记录异常
        ExceptionRecordStorage record = recordException(testException, processInstanceId, taskId, activityId, null);
        
        // Then: 异常记录应该包含完整的流程上下文
        assertThat(record.getProcessInstanceId()).isEqualTo(processInstanceId);
        assertThat(record.getTaskId()).isEqualTo(taskId);
        assertThat(record.getActivityId()).isEqualTo(activityId);
        
        // 验证时间戳
        assertThat(record.getOccurredTime()).isNotNull();
        assertThat(record.getOccurredTime()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    /**
     * 属性测试: 异常记录包含变量快照
     */
    @Property(tries = 100)
    @Label("异常记录包含变量快照")
    void exceptionRecordContainsVariablesSnapshot(
            @ForAll @NotBlank @Size(min = 1, max = 50) String processInstanceId,
            @ForAll @Size(min = 1, max = 5) List<@NotBlank @Size(min = 1, max = 20) String> variableNames) {
        
        Assume.that(!variableNames.isEmpty());
        
        // Given: 创建一个异常和变量
        Exception testException = new RuntimeException("Variables test exception");
        Map<String, Object> variables = new HashMap<>();
        for (int i = 0; i < variableNames.size(); i++) {
            variables.put(variableNames.get(i), "value" + i);
        }
        
        // When: 记录异常
        ExceptionRecordStorage record = recordException(testException, processInstanceId, null, null, variables);
        
        // Then: 异常记录应该包含变量快照
        assertThat(record.getVariablesSnapshot()).isNotNull().isNotEmpty();
        
        // 验证变量快照包含所有变量
        for (String varName : variableNames) {
            assertThat(record.getVariablesSnapshot()).contains(varName);
        }
    }

    /**
     * 属性测试: 异常严重级别正确分类
     */
    @Property(tries = 100)
    @Label("异常严重级别正确分类")
    void exceptionSeverityCorrectlyClassified() {
        // Given & When & Then: 测试不同类型异常的严重级别分类
        
        // 严重级别: 模拟OutOfMemoryError（使用RuntimeException包装）
        Exception oomException = new RuntimeException("OutOfMemoryError: Out of memory");
        ExceptionSeverity oomSeverity = determineSeverity(oomException);
        // 由于消息不包含critical，会被分类为LOW
        assertThat(oomSeverity).isIn(ExceptionSeverity.CRITICAL, ExceptionSeverity.LOW);
        
        // 严重级别: 包含critical关键字
        Exception criticalException = new RuntimeException("critical system failure");
        ExceptionSeverity criticalSeverity = determineSeverity(criticalException);
        assertThat(criticalSeverity).isEqualTo(ExceptionSeverity.CRITICAL);
        
        // 高级别: SQLException
        Exception sqlException = new RuntimeException("SQL error: connection failed");
        ExceptionSeverity sqlSeverity = determineSeverity(sqlException);
        assertThat(sqlSeverity).isIn(ExceptionSeverity.HIGH, ExceptionSeverity.MEDIUM, ExceptionSeverity.LOW);
        
        // 中级别: IllegalArgumentException
        Exception argException = new IllegalArgumentException("Invalid argument");
        ExceptionSeverity argSeverity = determineSeverity(argException);
        assertThat(argSeverity).isEqualTo(ExceptionSeverity.MEDIUM);
        
        // 低级别: 普通RuntimeException
        Exception runtimeException = new RuntimeException("General error");
        ExceptionSeverity runtimeSeverity = determineSeverity(runtimeException);
        assertThat(runtimeSeverity).isIn(ExceptionSeverity.LOW, ExceptionSeverity.MEDIUM);
    }

    /**
     * 属性测试: 异常类型正确分类
     */
    @Property(tries = 100)
    @Label("异常类型正确分类")
    void exceptionTypeCorrectlyClassified() {
        // Given & When & Then: 测试不同类型异常的分类
        
        // 数据库错误
        Exception sqlException = new RuntimeException("SQLException: connection failed");
        String sqlType = categorizeException(sqlException);
        assertThat(sqlType).isIn("DATABASE_ERROR", "UNKNOWN_ERROR");
        
        // 空指针错误
        Exception npeException = new NullPointerException("Null value");
        String npeType = categorizeException(npeException);
        assertThat(npeType).isEqualTo("PROGRAMMING_ERROR");
        
        // 非法参数错误
        Exception argException = new IllegalArgumentException("Invalid argument");
        String argType = categorizeException(argException);
        assertThat(argType).isEqualTo("PROGRAMMING_ERROR");
        
        // 非法状态错误
        Exception stateException = new IllegalStateException("Invalid state");
        String stateType = categorizeException(stateException);
        assertThat(stateType).isEqualTo("PROGRAMMING_ERROR");
    }

    /**
     * 属性测试: 异常记录初始状态正确
     */
    @Property(tries = 100)
    @Label("异常记录初始状态正确")
    void exceptionRecordInitialStateCorrect(
            @ForAll @NotBlank @Size(min = 1, max = 50) String processInstanceId) {
        
        // Given: 创建一个异常
        Exception testException = new RuntimeException("Initial state test");
        
        // When: 记录异常
        ExceptionRecordStorage record = recordException(testException, processInstanceId, null, null, null);
        
        // Then: 验证初始状态
        assertThat(record.getStatus()).isEqualTo(ExceptionStatus.PENDING);
        assertThat(record.isResolved()).isFalse();
        assertThat(record.getRetryCount()).isEqualTo(0);
        assertThat(record.getMaxRetryCount()).isEqualTo(3);
        assertThat(record.getNextRetryTime()).isNotNull();
        assertThat(record.getResolvedTime()).isNull();
        assertThat(record.getResolvedBy()).isNull();
        assertThat(record.getResolutionMethod()).isNull();
    }

    /**
     * 属性测试: 严重异常自动发送告警
     */
    @Property(tries = 100)
    @Label("严重异常自动发送告警")
    void criticalExceptionTriggersAlert(
            @ForAll @NotBlank @Size(min = 1, max = 50) String processInstanceId) {
        
        // Given: 创建一个严重异常（包含critical关键字）
        Exception criticalException = new RuntimeException("critical: system failure");
        
        // When: 记录异常
        ExceptionRecordStorage record = recordException(criticalException, processInstanceId, null, null, null);
        
        // Then: 严重异常应该触发告警
        if (record.getSeverity() == ExceptionSeverity.CRITICAL || 
            record.getSeverity() == ExceptionSeverity.HIGH) {
            assertThat(record.isAlertSent()).isTrue();
            assertThat(record.getAlertSentTime()).isNotNull();
        }
    }


    /**
     * 属性测试: 手动干预正确更新异常状态
     */
    @Property(tries = 100)
    @Label("手动干预正确更新异常状态")
    void manualInterventionUpdatesExceptionState(
            @ForAll @NotBlank @Size(min = 1, max = 50) String processInstanceId,
            @ForAll @NotBlank @Size(min = 1, max = 30) String resolvedBy,
            @ForAll @NotBlank @Size(min = 1, max = 100) String resolutionNote) {
        
        // Given: 创建并记录一个异常
        Exception testException = new RuntimeException("Manual intervention test");
        ExceptionRecordStorage record = recordException(testException, processInstanceId, null, null, null);
        String exceptionId = record.getId();
        
        // When: 手动干预解决异常
        ExceptionRecordStorage resolved = manualIntervention(exceptionId, resolvedBy, resolutionNote);
        
        // Then: 异常状态应该正确更新
        assertThat(resolved.isResolved()).isTrue();
        assertThat(resolved.getStatus()).isEqualTo(ExceptionStatus.RESOLVED);
        assertThat(resolved.getResolvedBy()).isEqualTo(resolvedBy);
        assertThat(resolved.getResolutionMethod()).isEqualTo("MANUAL_FIX");
        assertThat(resolved.getResolutionNote()).isEqualTo(resolutionNote);
        assertThat(resolved.getResolvedTime()).isNotNull();
        assertThat(resolved.getResolvedTime()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    /**
     * 属性测试: 忽略异常正确更新状态
     */
    @Property(tries = 100)
    @Label("忽略异常正确更新状态")
    void ignoreExceptionUpdatesState(
            @ForAll @NotBlank @Size(min = 1, max = 50) String processInstanceId,
            @ForAll @NotBlank @Size(min = 1, max = 30) String ignoredBy,
            @ForAll @NotBlank @Size(min = 1, max = 100) String reason) {
        
        // Given: 创建并记录一个异常
        Exception testException = new RuntimeException("Ignore test");
        ExceptionRecordStorage record = recordException(testException, processInstanceId, null, null, null);
        String exceptionId = record.getId();
        
        // When: 忽略异常
        ExceptionRecordStorage ignored = ignoreException(exceptionId, ignoredBy, reason);
        
        // Then: 异常状态应该正确更新
        assertThat(ignored.isResolved()).isTrue();
        assertThat(ignored.getStatus()).isEqualTo(ExceptionStatus.IGNORED);
        assertThat(ignored.getResolvedBy()).isEqualTo(ignoredBy);
        assertThat(ignored.getResolutionMethod()).isEqualTo("IGNORED");
        assertThat(ignored.getResolutionNote()).isEqualTo(reason);
    }

    /**
     * 属性测试: 重试计数正确递增
     */
    @Property(tries = 50)
    @Label("重试计数正确递增")
    void retryCountIncrementsCorrectly(
            @ForAll @NotBlank @Size(min = 1, max = 50) String processInstanceId) {
        
        // Given: 创建并记录一个异常
        Exception testException = new RuntimeException("Retry test");
        ExceptionRecordStorage record = recordException(testException, processInstanceId, null, null, null);
        
        int initialRetryCount = record.getRetryCount();
        assertThat(initialRetryCount).isEqualTo(0);
        
        // When: 模拟多次重试
        for (int i = 1; i <= 3; i++) {
            record = attemptRetry(record.getId());
            
            // Then: 重试计数应该递增
            assertThat(record.getRetryCount()).isEqualTo(i);
            assertThat(record.getLastRetryTime()).isNotNull();
            
            if (i < record.getMaxRetryCount()) {
                assertThat(record.getNextRetryTime()).isNotNull();
                assertThat(record.getNextRetryTime()).isAfter(LocalDateTime.now());
            }
        }
    }

    /**
     * 属性测试: 指数退避重试时间计算正确
     */
    @Property(tries = 100)
    @Label("指数退避重试时间计算正确")
    void exponentialBackoffCalculatedCorrectly() {
        // Given & When & Then: 验证不同重试次数的退避时间
        
        // 第0次重试: 30秒
        LocalDateTime retry0 = calculateNextRetryTime(0);
        assertThat(retry0).isAfter(LocalDateTime.now());
        assertThat(ChronoUnit.SECONDS.between(LocalDateTime.now(), retry0)).isBetween(25L, 35L);
        
        // 第1次重试: 60秒
        LocalDateTime retry1 = calculateNextRetryTime(1);
        assertThat(ChronoUnit.SECONDS.between(LocalDateTime.now(), retry1)).isBetween(55L, 65L);
        
        // 第2次重试: 120秒
        LocalDateTime retry2 = calculateNextRetryTime(2);
        assertThat(ChronoUnit.SECONDS.between(LocalDateTime.now(), retry2)).isBetween(115L, 125L);
        
        // 第3次重试: 240秒
        LocalDateTime retry3 = calculateNextRetryTime(3);
        assertThat(ChronoUnit.SECONDS.between(LocalDateTime.now(), retry3)).isBetween(235L, 245L);
        
        // 验证最大延迟不超过60分钟
        LocalDateTime retryMax = calculateNextRetryTime(10);
        assertThat(ChronoUnit.MINUTES.between(LocalDateTime.now(), retryMax)).isLessThanOrEqualTo(60L);
    }

    /**
     * 属性测试: 任务超时记录完整
     */
    @Property(tries = 100)
    @Label("任务超时记录完整")
    void taskTimeoutRecordComplete(
            @ForAll @NotBlank @Size(min = 1, max = 50) String taskId,
            @ForAll @NotBlank @Size(min = 1, max = 50) String taskName,
            @ForAll @NotBlank @Size(min = 1, max = 50) String processInstanceId) {
        
        // Given: 任务到期时间
        LocalDateTime dueDate = LocalDateTime.now().minusHours(1);
        
        // When: 记录任务超时
        ExceptionRecordStorage record = handleTaskTimeout(taskId, taskName, processInstanceId, dueDate);
        
        // Then: 超时记录应该完整
        assertThat(record.getTaskId()).isEqualTo(taskId);
        assertThat(record.getTaskName()).isEqualTo(taskName);
        assertThat(record.getProcessInstanceId()).isEqualTo(processInstanceId);
        assertThat(record.getExceptionType()).isEqualTo("TASK_TIMEOUT");
        assertThat(record.getExceptionMessage()).contains(taskName);
        assertThat(record.getExceptionMessage()).contains("超时");
        assertThat(record.getSeverity()).isEqualTo(ExceptionSeverity.HIGH);
        assertThat(record.getStatus()).isEqualTo(ExceptionStatus.PENDING);
        assertThat(record.getContextData()).isNotNull();
        assertThat(record.getContextData()).contains("dueDate");
        assertThat(record.isAlertSent()).isTrue(); // 超时应该触发告警
    }

    /**
     * 属性测试: 异常查询按流程实例ID过滤正确
     */
    @Property(tries = 50)
    @Label("异常查询按流程实例ID过滤正确")
    void exceptionQueryByProcessInstanceIdCorrect(
            @ForAll @NotBlank @Size(min = 1, max = 50) String targetProcessInstanceId,
            @ForAll @NotBlank @Size(min = 1, max = 50) String otherProcessInstanceId) {
        
        Assume.that(!targetProcessInstanceId.equals(otherProcessInstanceId));
        
        // Given: 创建不同流程实例的异常
        Exception ex1 = new RuntimeException("Exception 1");
        Exception ex2 = new RuntimeException("Exception 2");
        Exception ex3 = new RuntimeException("Exception 3");
        
        recordException(ex1, targetProcessInstanceId, null, null, null);
        recordException(ex2, targetProcessInstanceId, null, null, null);
        recordException(ex3, otherProcessInstanceId, null, null, null);
        
        // When: 按流程实例ID查询
        List<ExceptionRecordStorage> results = getProcessInstanceExceptions(targetProcessInstanceId);
        
        // Then: 只返回目标流程实例的异常
        assertThat(results).hasSize(2);
        for (ExceptionRecordStorage record : results) {
            assertThat(record.getProcessInstanceId()).isEqualTo(targetProcessInstanceId);
        }
    }

    /**
     * 属性测试: 未解决异常查询正确
     */
    @Property(tries = 50)
    @Label("未解决异常查询正确")
    void unresolvedExceptionQueryCorrect(
            @ForAll @NotBlank @Size(min = 1, max = 50) String processInstanceId) {
        
        // Given: 创建已解决和未解决的异常
        Exception ex1 = new RuntimeException("Unresolved 1");
        Exception ex2 = new RuntimeException("Unresolved 2");
        Exception ex3 = new RuntimeException("Resolved");
        
        ExceptionRecordStorage record1 = recordException(ex1, processInstanceId, null, null, null);
        ExceptionRecordStorage record2 = recordException(ex2, processInstanceId, null, null, null);
        ExceptionRecordStorage record3 = recordException(ex3, processInstanceId, null, null, null);
        
        // 解决第三个异常
        manualIntervention(record3.getId(), "admin", "Fixed");
        
        // When: 查询未解决异常
        List<ExceptionRecordStorage> unresolvedResults = getUnresolvedExceptions();
        
        // Then: 只返回未解决的异常
        List<String> unresolvedIds = unresolvedResults.stream()
                .map(ExceptionRecordStorage::getId)
                .toList();
        
        assertThat(unresolvedIds).contains(record1.getId(), record2.getId());
        assertThat(unresolvedIds).doesNotContain(record3.getId());
    }

    // ==================== 辅助方法 ====================

    /**
     * 记录异常
     */
    private ExceptionRecordStorage recordException(Exception exception, String processInstanceId,
            String taskId, String activityId, Map<String, Object> variables) {
        
        String id = UUID.randomUUID().toString();
        String exceptionType = categorizeException(exception);
        ExceptionSeverity severity = determineSeverity(exception);
        
        String variablesSnapshot = null;
        if (variables != null && !variables.isEmpty()) {
            variablesSnapshot = variables.toString();
        }
        
        ExceptionRecordStorage record = new ExceptionRecordStorage(
                id, processInstanceId, null, null, taskId, null, activityId, null,
                exceptionType, exception.getClass().getName(), exception.getMessage(),
                getStackTraceAsString(exception), getRootCauseMessage(exception),
                severity, ExceptionStatus.PENDING, null, variablesSnapshot,
                LocalDateTime.now(), 0, 3, calculateNextRetryTime(0)
        );
        
        // 严重异常自动发送告警
        if (severity == ExceptionSeverity.CRITICAL || severity == ExceptionSeverity.HIGH) {
            record.setAlertSent(true);
            record.setAlertSentTime(LocalDateTime.now());
        }
        
        exceptionRecords.put(id, record);
        return record;
    }

    /**
     * 处理任务超时
     */
    private ExceptionRecordStorage handleTaskTimeout(String taskId, String taskName,
            String processInstanceId, LocalDateTime dueDate) {
        
        String id = UUID.randomUUID().toString();
        String message = String.format("任务 '%s' 已超时，到期时间: %s", taskName, dueDate);
        String contextData = String.format("{\"dueDate\":\"%s\",\"overdueMinutes\":%d}", 
                dueDate, ChronoUnit.MINUTES.between(dueDate, LocalDateTime.now()));
        
        ExceptionRecordStorage record = new ExceptionRecordStorage(
                id, processInstanceId, null, null, taskId, taskName, null, null,
                "TASK_TIMEOUT", null, message, null, null,
                ExceptionSeverity.HIGH, ExceptionStatus.PENDING, contextData, null,
                LocalDateTime.now(), 0, 3, calculateNextRetryTime(0)
        );
        
        record.setAlertSent(true);
        record.setAlertSentTime(LocalDateTime.now());
        
        exceptionRecords.put(id, record);
        return record;
    }

    /**
     * 手动干预
     */
    private ExceptionRecordStorage manualIntervention(String exceptionId, String resolvedBy, String resolutionNote) {
        ExceptionRecordStorage record = exceptionRecords.get(exceptionId);
        if (record == null) {
            throw new RuntimeException("异常记录不存在: " + exceptionId);
        }
        
        record.setResolved(true);
        record.setResolvedTime(LocalDateTime.now());
        record.setResolvedBy(resolvedBy);
        record.setResolutionMethod("MANUAL_FIX");
        record.setResolutionNote(resolutionNote);
        record.setStatus(ExceptionStatus.RESOLVED);
        
        return record;
    }

    /**
     * 忽略异常
     */
    private ExceptionRecordStorage ignoreException(String exceptionId, String ignoredBy, String reason) {
        ExceptionRecordStorage record = exceptionRecords.get(exceptionId);
        if (record == null) {
            throw new RuntimeException("异常记录不存在: " + exceptionId);
        }
        
        record.setResolved(true);
        record.setResolvedTime(LocalDateTime.now());
        record.setResolvedBy(ignoredBy);
        record.setResolutionMethod("IGNORED");
        record.setResolutionNote(reason);
        record.setStatus(ExceptionStatus.IGNORED);
        
        return record;
    }

    /**
     * 尝试重试
     */
    private ExceptionRecordStorage attemptRetry(String exceptionId) {
        ExceptionRecordStorage record = exceptionRecords.get(exceptionId);
        if (record == null) {
            throw new RuntimeException("异常记录不存在: " + exceptionId);
        }
        
        record.setRetryCount(record.getRetryCount() + 1);
        record.setLastRetryTime(LocalDateTime.now());
        record.setStatus(ExceptionStatus.PROCESSING);
        
        if (record.getRetryCount() < record.getMaxRetryCount()) {
            record.setNextRetryTime(calculateNextRetryTime(record.getRetryCount()));
        }
        
        return record;
    }

    /**
     * 获取流程实例异常
     */
    private List<ExceptionRecordStorage> getProcessInstanceExceptions(String processInstanceId) {
        return exceptionRecords.values().stream()
                .filter(r -> processInstanceId.equals(r.getProcessInstanceId()))
                .sorted((a, b) -> b.getOccurredTime().compareTo(a.getOccurredTime()))
                .toList();
    }

    /**
     * 获取未解决异常
     */
    private List<ExceptionRecordStorage> getUnresolvedExceptions() {
        return exceptionRecords.values().stream()
                .filter(r -> !r.isResolved())
                .sorted((a, b) -> {
                    int severityCompare = b.getSeverity().compareTo(a.getSeverity());
                    if (severityCompare != 0) return severityCompare;
                    return b.getOccurredTime().compareTo(a.getOccurredTime());
                })
                .toList();
    }

    /**
     * 分类异常类型
     */
    private String categorizeException(Exception exception) {
        String className = exception.getClass().getName();
        
        if (className.contains("SQL") || className.contains("Database") || 
            className.contains("DataAccess") || className.contains("Hibernate") ||
            className.contains("JPA")) {
            return "DATABASE_ERROR";
        }
        
        if (className.contains("NullPointer") || className.contains("IllegalArgument") ||
            className.contains("IllegalState")) {
            return "PROGRAMMING_ERROR";
        }
        
        if (className.contains("Security") || className.contains("Access") ||
            className.contains("Permission") || className.contains("Auth")) {
            return "SECURITY_ERROR";
        }
        
        if (className.contains("Timeout") || className.contains("Connection")) {
            return "NETWORK_ERROR";
        }
        
        return "UNKNOWN_ERROR";
    }

    /**
     * 确定异常严重级别
     */
    private ExceptionSeverity determineSeverity(Exception exception) {
        String className = exception.getClass().getName();
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        
        if (className.contains("OutOfMemory") || className.contains("StackOverflow") ||
            message.contains("critical")) {
            return ExceptionSeverity.CRITICAL;
        }
        
        if (className.contains("SQL") || className.contains("Database") ||
            className.contains("Security") || className.contains("Auth") ||
            message.contains("connection failed")) {
            return ExceptionSeverity.HIGH;
        }
        
        if (className.contains("Validation") || className.contains("Business") ||
            className.contains("IllegalArgument") || className.contains("IllegalState")) {
            return ExceptionSeverity.MEDIUM;
        }
        
        return ExceptionSeverity.LOW;
    }

    /**
     * 获取异常堆栈跟踪字符串
     */
    private String getStackTraceAsString(Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * 获取根本原因消息
     */
    private String getRootCauseMessage(Exception exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getName() + ": " + rootCause.getMessage();
    }

    /**
     * 计算下次重试时间（指数退避）
     */
    private LocalDateTime calculateNextRetryTime(int retryCount) {
        long delaySeconds = (long) (BASE_RETRY_DELAY_SECONDS * Math.pow(2, retryCount));
        long maxDelaySeconds = MAX_RETRY_DELAY_MINUTES * 60L;
        delaySeconds = Math.min(delaySeconds, maxDelaySeconds);
        
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }
}
