package com.workflow.component;

import com.workflow.entity.N8nExecutionRecord;
import com.workflow.repository.N8nExecutionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * N8N 超时检查定时任务
 * 每 60 秒扫描处于 RUNNING 状态且已超过超时时间的执行记录，
 * 将其标记为 TIMEOUT 并触发 Flowable 异常处理。
 *
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class N8nTimeoutChecker {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_TIMEOUT = "TIMEOUT";

    private final N8nExecutionRecordRepository executionRecordRepository;
    private final RuntimeService runtimeService;

    /**
     * 定时扫描超时的 N8N 执行记录。
     * 每 60 秒执行一次，查找所有 RUNNING 状态且已超时的记录，
     * 将其标记为 TIMEOUT 并尝试触发 Flowable 异常处理。
     */
    @Scheduled(fixedRate = 60000)
    public void checkTimeouts() {
        Instant now = Instant.now();
        log.debug("N8N timeout check started at {}", now);

        // Query all RUNNING records that have started before now
        // (effectively all RUNNING records), then filter per-record timeout in code
        List<N8nExecutionRecord> runningRecords =
                executionRecordRepository.findByStatusAndStartedAtBefore(STATUS_RUNNING, now);

        if (runningRecords.isEmpty()) {
            log.debug("No RUNNING N8N execution records found");
            return;
        }

        int timedOutCount = 0;
        for (N8nExecutionRecord record : runningRecords) {
            if (isTimedOut(record, now)) {
                handleTimeout(record, now);
                timedOutCount++;
            }
        }

        if (timedOutCount > 0) {
            log.info("N8N timeout check completed: {} records timed out", timedOutCount);
        }
    }

    /**
     * 判断执行记录是否已超时。
     * 超时条件：当前时间 > startedAt + timeoutSeconds
     *
     * @param record 执行记录
     * @param now    当前时间
     * @return true 如果已超时
     */
    public boolean isTimedOut(N8nExecutionRecord record, Instant now) {
        if (record.getStartedAt() == null || record.getTimeoutSeconds() == null) {
            return false;
        }
        Instant deadline = record.getStartedAt().plusSeconds(record.getTimeoutSeconds());
        return now.isAfter(deadline);
    }

    /**
     * 处理超时记录：标记为 TIMEOUT，记录错误日志，尝试触发 Flowable 异常处理。
     */
    private void handleTimeout(N8nExecutionRecord record, Instant now) {
        log.warn("N8N execution timed out: recordId={}, processInstanceId={}, taskId={}, " +
                        "startedAt={}, timeoutSeconds={}, elapsed={}s",
                record.getId(), record.getProcessInstanceId(), record.getTaskId(),
                record.getStartedAt(), record.getTimeoutSeconds(),
                java.time.Duration.between(record.getStartedAt(), now).getSeconds());

        record.setStatus(STATUS_TIMEOUT);
        record.setErrorMessage("N8N workflow execution timed out after " + record.getTimeoutSeconds() + " seconds");
        record.setCompletedAt(now);
        executionRecordRepository.save(record);

        // Best-effort: trigger Flowable error handling
        if (record.getTaskId() != null) {
            try {
                runtimeService.trigger(record.getTaskId());
                log.info("Triggered Flowable error handling for timed-out execution: recordId={}", record.getId());
            } catch (Exception e) {
                log.warn("Failed to trigger Flowable error handling for timed-out execution: recordId={}, error={}",
                        record.getId(), e.getMessage());
            }
        }
    }
}
