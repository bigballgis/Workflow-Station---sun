package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.ExceptionQueryRequest;
import com.workflow.dto.ExceptionStatisticsResult;
import com.workflow.entity.ExceptionRecord;
import com.workflow.entity.ExceptionRecord.ExceptionSeverity;
import com.workflow.entity.ExceptionRecord.ExceptionStatus;
import com.workflow.repository.ExceptionRecordRepository;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class ExceptionHandlerComponent {
    
    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlerComponent.class);
    private static final int BASE_RETRY_DELAY_SECONDS = 30;
    private static final int MAX_RETRY_DELAY_MINUTES = 60;
    
    @Autowired
    private ExceptionRecordRepository exceptionRecordRepository;
    
    @Autowired(required = false)
    private RuntimeService runtimeService;
    
    @Autowired(required = false)
    private NotificationManagerComponent notificationManager;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public ExceptionRecord recordException(Exception exception, String processInstanceId, 
            String taskId, String activityId, Map<String, Object> variables) {
        log.info("Recording exception: processInstanceId={}, taskId={}", processInstanceId, taskId);
        
        ExceptionRecord record = new ExceptionRecord();
        record.setProcessInstanceId(processInstanceId);
        record.setTaskId(taskId);
        record.setActivityId(activityId);
        record.setExceptionClass(exception.getClass().getName());
        record.setExceptionMessage(exception.getMessage());
        record.setStackTrace(getStackTraceAsString(exception));
        record.setRootCause(getRootCauseMessage(exception));
        record.setExceptionType(classifyExceptionType(exception));
        record.setSeverity(determineSeverity(exception));
        record.setStatus(ExceptionStatus.PENDING);
        record.setResolved(false);
        record.setRetryCount(0);
        record.setOccurredTime(LocalDateTime.now());
        
        if (variables != null && !variables.isEmpty()) {
            try {
                record.setVariablesSnapshot(objectMapper.writeValueAsString(variables));
            } catch (Exception e) {
                log.warn("Failed to serialize variables snapshot: {}", e.getMessage());
            }
        }
        
        if (processInstanceId != null && runtimeService != null) {
            try {
                ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .singleResult();
                if (instance != null) {
                    record.setProcessDefinitionId(instance.getProcessDefinitionId());
                    record.setProcessDefinitionKey(instance.getProcessDefinitionKey());
                }
            } catch (Exception e) {
                log.warn("Failed to get process definition info: {}", e.getMessage());
            }
        }
        
        ExceptionRecord savedRecord = exceptionRecordRepository.save(record);
        log.info("Exception recorded: id={}, type={}, severity={}", 
                savedRecord.getId(), savedRecord.getExceptionType(), savedRecord.getSeverity());
        
        if (shouldSendAlert(savedRecord)) {
            sendAlert(savedRecord);
        }
        
        return savedRecord;
    }

    @Transactional
    public ExceptionRecord recordTaskTimeout(String processInstanceId, String taskId, 
            String taskName, LocalDateTime dueDate) {
        log.info("Recording task timeout: taskId={}, taskName={}", taskId, taskName);
        
        ExceptionRecord record = new ExceptionRecord();
        record.setProcessInstanceId(processInstanceId);
        record.setTaskId(taskId);
        record.setTaskName(taskName);
        record.setExceptionType("TASK_TIMEOUT");
        record.setExceptionClass("TaskTimeoutException");
        record.setExceptionMessage("Task exceeded due date: " + dueDate);
        record.setSeverity(ExceptionSeverity.MEDIUM);
        record.setStatus(ExceptionStatus.PENDING);
        record.setResolved(false);
        record.setRetryCount(0);
        record.setOccurredTime(LocalDateTime.now());
        
        return exceptionRecordRepository.save(record);
    }

    public String classifyExceptionType(Exception exception) {
        String className = exception.getClass().getName().toLowerCase();
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        
        if (className.contains("sql") || className.contains("jdbc") || 
            className.contains("hibernate") || className.contains("jpa") ||
            className.contains("dataaccess") || className.contains("transaction")) {
            return "DATABASE_ERROR";
        }
        if (className.contains("socket") || className.contains("connection") ||
            className.contains("timeout") || className.contains("http") ||
            message.contains("connection refused") || message.contains("timed out")) {
            return "NETWORK_ERROR";
        }
        if (className.contains("business") || className.contains("workflow") ||
            className.contains("validation") || className.contains("illegal")) {
            return "BUSINESS_ERROR";
        }
        if (className.contains("security") || className.contains("access") ||
            className.contains("permission") || className.contains("auth")) {
            return "SECURITY_ERROR";
        }
        if (className.contains("resource") || className.contains("notfound") ||
            className.contains("nosuch") || message.contains("not found")) {
            return "RESOURCE_NOT_FOUND";
        }
        if (className.contains("config") || className.contains("property") ||
            className.contains("bean")) {
            return "CONFIGURATION_ERROR";
        }
        if (exception instanceof NullPointerException ||
            exception instanceof IllegalArgumentException ||
            exception instanceof IllegalStateException ||
            exception instanceof IndexOutOfBoundsException) {
            return "PROGRAMMING_ERROR";
        }
        return "UNKNOWN_ERROR";
    }

    public ExceptionSeverity determineSeverity(Exception exception) {
        String className = exception.getClass().getName().toLowerCase();
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        
        if (className.contains("outofmemory") || className.contains("stackoverflow") ||
            className.contains("systemerror") || message.contains("system failure") ||
            message.contains("critical")) {
            return ExceptionSeverity.CRITICAL;
        }
        if (className.contains("sql") || className.contains("security") ||
            className.contains("dataintegrity") || className.contains("deadlock") ||
            message.contains("data corruption") || message.contains("unauthorized")) {
            return ExceptionSeverity.HIGH;
        }
        if (className.contains("business") || className.contains("timeout") ||
            className.contains("connection") || className.contains("validation")) {
            return ExceptionSeverity.MEDIUM;
        }
        return ExceptionSeverity.LOW;
    }

    @Transactional
    public ExceptionRecord handleManualIntervention(String exceptionId, String resolvedBy, 
            String resolutionNote) {
        log.info("Manual intervention for exception: id={}, resolvedBy={}", exceptionId, resolvedBy);
        
        ExceptionRecord record = exceptionRecordRepository.findById(exceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Exception record not found: " + exceptionId));
        
        record.setStatus(ExceptionStatus.RESOLVED);
        record.setResolved(true);
        record.setResolvedTime(LocalDateTime.now());
        record.setResolvedBy(resolvedBy);
        record.setResolutionMethod("MANUAL_FIX");
        record.setResolutionNote(resolutionNote);
        
        return exceptionRecordRepository.save(record);
    }

    @Transactional
    public ExceptionRecord ignoreException(String exceptionId, String ignoredBy, String reason) {
        log.info("Ignoring exception: id={}, ignoredBy={}", exceptionId, ignoredBy);
        
        ExceptionRecord record = exceptionRecordRepository.findById(exceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Exception record not found: " + exceptionId));
        
        record.setStatus(ExceptionStatus.IGNORED);
        record.setResolved(true);
        record.setResolvedTime(LocalDateTime.now());
        record.setResolvedBy(ignoredBy);
        record.setResolutionMethod("IGNORED");
        record.setResolutionNote(reason);
        
        return exceptionRecordRepository.save(record);
    }

    @Transactional
    public ExceptionRecord incrementRetryCount(String exceptionId) {
        ExceptionRecord record = exceptionRecordRepository.findById(exceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Exception record not found: " + exceptionId));
        
        record.setRetryCount(record.getRetryCount() + 1);
        record.setLastRetryTime(LocalDateTime.now());
        record.setNextRetryTime(calculateNextRetryTime(record.getRetryCount()));
        
        if (record.getRetryCount() >= record.getMaxRetryCount()) {
            record.setStatus(ExceptionStatus.PROCESSING);
            log.warn("Exception reached max retry count: id={}, retryCount={}", 
                    exceptionId, record.getRetryCount());
        }
        
        return exceptionRecordRepository.save(record);
    }

    public LocalDateTime calculateNextRetryTime(int retryCount) {
        long delaySeconds = (long) (BASE_RETRY_DELAY_SECONDS * Math.pow(2, retryCount - 1));
        long maxDelaySeconds = MAX_RETRY_DELAY_MINUTES * 60L;
        delaySeconds = Math.min(delaySeconds, maxDelaySeconds);
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }

    @Transactional
    public ExceptionRecord markAsResolved(String exceptionId) {
        ExceptionRecord record = exceptionRecordRepository.findById(exceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Exception record not found: " + exceptionId));
        
        record.setStatus(ExceptionStatus.RESOLVED);
        record.setResolved(true);
        record.setResolvedTime(LocalDateTime.now());
        record.setResolutionMethod("AUTO_RETRY");
        
        return exceptionRecordRepository.save(record);
    }

    public List<ExceptionRecord> getExceptionsByProcessInstanceId(String processInstanceId) {
        return exceptionRecordRepository.findByProcessInstanceIdOrderByOccurredTimeDesc(processInstanceId);
    }

    public List<ExceptionRecord> getUnresolvedExceptions() {
        return exceptionRecordRepository.findByResolvedFalseOrderBySeverityDescOccurredTimeDesc();
    }

    public List<ExceptionRecord> getPendingRetryExceptions() {
        return exceptionRecordRepository.findPendingRetryExceptions(LocalDateTime.now());
    }

    public Page<ExceptionRecord> queryExceptions(ExceptionQueryRequest request) {
        Sort sort = Sort.by(
                "DESC".equalsIgnoreCase(request.getSortDirection()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy()
        );
        PageRequest pageRequest = PageRequest.of(request.getPage(), request.getSize(), sort);
        
        if (request.getStatus() != null && request.getSeverity() != null) {
            return exceptionRecordRepository.findByStatusAndSeverity(
                    request.getStatus(), request.getSeverity(), pageRequest);
        }
        return exceptionRecordRepository.findAll(pageRequest);
    }

    public ExceptionStatisticsResult getExceptionStatistics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.truncatedTo(ChronoUnit.DAYS);
        LocalDateTime weekStart = now.minusDays(7).truncatedTo(ChronoUnit.DAYS);
        LocalDateTime monthStart = now.minusDays(30).truncatedTo(ChronoUnit.DAYS);
        
        List<ExceptionRecord> allRecords = exceptionRecordRepository.findAll();
        
        long totalCount = allRecords.size();
        long unresolvedCount = allRecords.stream().filter(r -> !r.getResolved()).count();
        long resolvedCount = totalCount - unresolvedCount;
        
        long criticalCount = allRecords.stream()
                .filter(r -> r.getSeverity() == ExceptionSeverity.CRITICAL && !r.getResolved()).count();
        long highCount = allRecords.stream()
                .filter(r -> r.getSeverity() == ExceptionSeverity.HIGH && !r.getResolved()).count();
        long mediumCount = allRecords.stream()
                .filter(r -> r.getSeverity() == ExceptionSeverity.MEDIUM && !r.getResolved()).count();
        long lowCount = allRecords.stream()
                .filter(r -> r.getSeverity() == ExceptionSeverity.LOW && !r.getResolved()).count();
        
        long pendingCount = allRecords.stream()
                .filter(r -> r.getStatus() == ExceptionStatus.PENDING).count();
        long processingCount = allRecords.stream()
                .filter(r -> r.getStatus() == ExceptionStatus.PROCESSING).count();
        long ignoredCount = allRecords.stream()
                .filter(r -> r.getStatus() == ExceptionStatus.IGNORED).count();
        
        long todayCount = allRecords.stream()
                .filter(r -> r.getOccurredTime().isAfter(todayStart)).count();
        long thisWeekCount = allRecords.stream()
                .filter(r -> r.getOccurredTime().isAfter(weekStart)).count();
        long thisMonthCount = allRecords.stream()
                .filter(r -> r.getOccurredTime().isAfter(monthStart)).count();
        
        Map<String, Long> countByProcessDefinition = new HashMap<>();
        allRecords.stream()
                .filter(r -> r.getProcessDefinitionKey() != null)
                .forEach(r -> countByProcessDefinition.merge(r.getProcessDefinitionKey(), 1L, Long::sum));
        
        Map<String, Long> countByExceptionType = new HashMap<>();
        allRecords.forEach(r -> countByExceptionType.merge(r.getExceptionType(), 1L, Long::sum));
        
        return ExceptionStatisticsResult.builder()
                .totalCount(totalCount)
                .unresolvedCount(unresolvedCount)
                .resolvedCount(resolvedCount)
                .criticalCount(criticalCount)
                .highCount(highCount)
                .mediumCount(mediumCount)
                .lowCount(lowCount)
                .pendingCount(pendingCount)
                .processingCount(processingCount)
                .ignoredCount(ignoredCount)
                .countByProcessDefinition(countByProcessDefinition)
                .countByExceptionType(countByExceptionType)
                .todayCount(todayCount)
                .thisWeekCount(thisWeekCount)
                .thisMonthCount(thisMonthCount)
                .build();
    }

    @Transactional
    public List<String> recoverInterruptedProcesses() {
        log.info("Starting recovery of interrupted processes");
        
        List<String> interruptedIds = exceptionRecordRepository.findInterruptedProcessInstanceIds();
        List<String> recoveredIds = new ArrayList<>();
        
        for (String processInstanceId : interruptedIds) {
            try {
                if (runtimeService != null) {
                    ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                            .processInstanceId(processInstanceId)
                            .singleResult();
                    
                    if (instance != null && instance.isSuspended()) {
                        runtimeService.activateProcessInstanceById(processInstanceId);
                        recoveredIds.add(processInstanceId);
                        log.info("Recovered process instance: {}", processInstanceId);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to recover process instance: {}, error: {}", 
                        processInstanceId, e.getMessage());
            }
        }
        
        log.info("Recovery completed: {} processes recovered", recoveredIds.size());
        return recoveredIds;
    }

    public List<ExceptionRecord> getExceptionsNeedingAlert() {
        return exceptionRecordRepository.findExceptionsNeedingAlert();
    }

    private boolean shouldSendAlert(ExceptionRecord record) {
        return record.getSeverity() == ExceptionSeverity.CRITICAL ||
               record.getSeverity() == ExceptionSeverity.HIGH;
    }

    private void sendAlert(ExceptionRecord record) {
        try {
            if (notificationManager != null) {
                Map<String, Object> alertData = new HashMap<>();
                alertData.put("exceptionId", record.getId());
                alertData.put("exceptionType", record.getExceptionType());
                alertData.put("severity", record.getSeverity().name());
                alertData.put("message", record.getExceptionMessage());
                alertData.put("processInstanceId", record.getProcessInstanceId());
                alertData.put("occurredTime", record.getOccurredTime().toString());
                
                log.info("Sending alert for exception: id={}, severity={}", 
                        record.getId(), record.getSeverity());
            }
            
            record.setAlertSent(true);
            record.setAlertSentTime(LocalDateTime.now());
            exceptionRecordRepository.save(record);
            
        } catch (Exception e) {
            log.error("Failed to send alert for exception: {}, error: {}", 
                    record.getId(), e.getMessage());
        }
    }

    @Transactional
    public void cleanupExpiredExceptions(int retentionDays) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
        log.info("Cleaning up resolved exceptions before: {}", cutoffTime);
        exceptionRecordRepository.deleteByResolvedTrueAndResolvedTimeBefore(cutoffTime);
    }

    private String getStackTraceAsString(Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }

    private String getRootCauseMessage(Exception exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getName() + ": " + rootCause.getMessage();
    }
}
