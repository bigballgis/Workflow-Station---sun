package com.workflow.component;

import com.workflow.dto.ExceptionQueryRequest;
import com.workflow.dto.ExceptionStatisticsResult;
import com.workflow.entity.ExceptionRecord;
import com.workflow.entity.ExceptionRecord.ExceptionSeverity;
import com.workflow.entity.ExceptionRecord.ExceptionStatus;
import com.workflow.repository.ExceptionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 异常处理组件单元测试
 * 需求: 9.1, 9.2, 9.6, 9.7, 9.8
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("异常处理组件测试")
class ExceptionHandlerComponentTest {

    @Mock
    private ExceptionRecordRepository exceptionRecordRepository;

    @InjectMocks
    private ExceptionHandlerComponent exceptionHandler;

    private ExceptionRecord testRecord;

    @BeforeEach
    void setUp() {
        testRecord = new ExceptionRecord();
        testRecord.setId("test-exception-id");
        testRecord.setProcessInstanceId("proc-123");
        testRecord.setTaskId("task-456");
        testRecord.setActivityId("activity-789");
        testRecord.setExceptionType("PROGRAMMING_ERROR");
        testRecord.setExceptionClass("java.lang.RuntimeException");
        testRecord.setExceptionMessage("Test exception message");
        testRecord.setStackTrace("java.lang.RuntimeException: Test\n\tat Test.method(Test.java:10)");
        testRecord.setRootCause("java.lang.RuntimeException: Test exception message");
        testRecord.setSeverity(ExceptionSeverity.MEDIUM);
        testRecord.setStatus(ExceptionStatus.PENDING);
        testRecord.setOccurredTime(LocalDateTime.now());
        testRecord.setRetryCount(0);
        testRecord.setMaxRetryCount(3);
        testRecord.setResolved(false);
    }

    @Nested
    @DisplayName("异常记录测试")
    class ExceptionRecordingTests {

        @Test
        @DisplayName("记录异常应该保存完整的异常信息")
        void recordException_shouldSaveCompleteExceptionInfo() {
            // Given
            Exception exception = new RuntimeException("Test error", new IllegalArgumentException("Root cause"));
            when(exceptionRecordRepository.save(any(ExceptionRecord.class))).thenAnswer(invocation -> {
                ExceptionRecord record = invocation.getArgument(0);
                record.setId(UUID.randomUUID().toString());
                return record;
            });

            // When
            ExceptionRecord result = exceptionHandler.recordException(
                    exception, "proc-123", "task-456", "activity-789", null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getProcessInstanceId()).isEqualTo("proc-123");
            assertThat(result.getTaskId()).isEqualTo("task-456");
            assertThat(result.getActivityId()).isEqualTo("activity-789");
            assertThat(result.getExceptionClass()).isEqualTo("java.lang.RuntimeException");
            assertThat(result.getExceptionMessage()).isEqualTo("Test error");
            assertThat(result.getStackTrace()).contains("RuntimeException");
            assertThat(result.getRootCause()).contains("IllegalArgumentException");
            assertThat(result.getStatus()).isEqualTo(ExceptionStatus.PENDING);
            assertThat(result.getRetryCount()).isEqualTo(0);
            
            verify(exceptionRecordRepository).save(any(ExceptionRecord.class));
        }

        @Test
        @DisplayName("记录异常应该保存变量快照")
        void recordException_shouldSaveVariablesSnapshot() {
            // Given
            Exception exception = new RuntimeException("Test error");
            Map<String, Object> variables = new HashMap<>();
            variables.put("var1", "value1");
            variables.put("var2", 123);
            
            when(exceptionRecordRepository.save(any(ExceptionRecord.class))).thenAnswer(invocation -> {
                ExceptionRecord record = invocation.getArgument(0);
                record.setId(UUID.randomUUID().toString());
                return record;
            });

            // When
            ExceptionRecord result = exceptionHandler.recordException(
                    exception, "proc-123", null, null, variables);

            // Then
            assertThat(result.getVariablesSnapshot()).isNotNull();
            assertThat(result.getVariablesSnapshot()).contains("var1");
            assertThat(result.getVariablesSnapshot()).contains("value1");
        }

        @Test
        @DisplayName("严重异常应该自动发送告警")
        void recordException_criticalShouldTriggerAlert() {
            // Given
            Exception exception = new RuntimeException("critical system failure");
            when(exceptionRecordRepository.save(any(ExceptionRecord.class))).thenAnswer(invocation -> {
                ExceptionRecord record = invocation.getArgument(0);
                record.setId(UUID.randomUUID().toString());
                return record;
            });

            // When
            ExceptionRecord result = exceptionHandler.recordException(
                    exception, "proc-123", null, null, null);

            // Then
            assertThat(result.getSeverity()).isEqualTo(ExceptionSeverity.CRITICAL);
            assertThat(result.getAlertSent()).isTrue();
            assertThat(result.getAlertSentTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("手动干预测试")
    class ManualInterventionTests {

        @Test
        @DisplayName("手动干预应该正确更新异常状态")
        void manualIntervention_shouldUpdateExceptionState() {
            // Given
            when(exceptionRecordRepository.findById("test-exception-id")).thenReturn(Optional.of(testRecord));
            when(exceptionRecordRepository.save(any(ExceptionRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ExceptionRecord result = exceptionHandler.handleManualIntervention(
                    "test-exception-id", "admin", "Fixed manually");

            // Then
            assertThat(result.getResolved()).isTrue();
            assertThat(result.getStatus()).isEqualTo(ExceptionStatus.RESOLVED);
            assertThat(result.getResolvedBy()).isEqualTo("admin");
            assertThat(result.getResolutionMethod()).isEqualTo("MANUAL_FIX");
            assertThat(result.getResolutionNote()).isEqualTo("Fixed manually");
            assertThat(result.getResolvedTime()).isNotNull();
        }

        @Test
        @DisplayName("手动干预不存在的异常应该抛出异常")
        void manualIntervention_nonExistentShouldThrow() {
            // Given
            when(exceptionRecordRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> exceptionHandler.handleManualIntervention("non-existent", "admin", "note"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("忽略异常测试")
    class IgnoreExceptionTests {

        @Test
        @DisplayName("忽略异常应该正确更新状态")
        void ignoreException_shouldUpdateState() {
            // Given
            when(exceptionRecordRepository.findById("test-exception-id")).thenReturn(Optional.of(testRecord));
            when(exceptionRecordRepository.save(any(ExceptionRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ExceptionRecord result = exceptionHandler.ignoreException(
                    "test-exception-id", "admin", "Not important");

            // Then
            assertThat(result.getResolved()).isTrue();
            assertThat(result.getStatus()).isEqualTo(ExceptionStatus.IGNORED);
            assertThat(result.getResolvedBy()).isEqualTo("admin");
            assertThat(result.getResolutionMethod()).isEqualTo("IGNORED");
            assertThat(result.getResolutionNote()).isEqualTo("Not important");
        }
    }

    @Nested
    @DisplayName("任务超时处理测试")
    class TaskTimeoutTests {

        @Test
        @DisplayName("处理任务超时应该创建正确的异常记录")
        void handleTaskTimeout_shouldCreateCorrectRecord() {
            // Given
            LocalDateTime dueDate = LocalDateTime.now().minusHours(1);
            when(exceptionRecordRepository.save(any(ExceptionRecord.class))).thenAnswer(invocation -> {
                ExceptionRecord record = invocation.getArgument(0);
                record.setId(UUID.randomUUID().toString());
                return record;
            });

            // When
            ExceptionRecord result = exceptionHandler.recordTaskTimeout(
                    "proc-456", "task-123", "审批任务", dueDate);

            // Then
            assertThat(result.getTaskId()).isEqualTo("task-123");
            assertThat(result.getTaskName()).isEqualTo("审批任务");
            assertThat(result.getProcessInstanceId()).isEqualTo("proc-456");
            assertThat(result.getExceptionType()).isEqualTo("TASK_TIMEOUT");
            assertThat(result.getExceptionMessage()).contains("due date");
            assertThat(result.getSeverity()).isEqualTo(ExceptionSeverity.MEDIUM);
        }
    }

    @Nested
    @DisplayName("异常查询测试")
    class ExceptionQueryTests {

        @Test
        @DisplayName("查询异常应该返回分页结果")
        void queryExceptions_shouldReturnPagedResults() {
            // Given
            ExceptionQueryRequest request = new ExceptionQueryRequest();
            request.setPage(0);
            request.setSize(10);
            request.setStatus(ExceptionStatus.PENDING);
            request.setSeverity(ExceptionSeverity.HIGH);

            List<ExceptionRecord> records = Arrays.asList(testRecord);
            Page<ExceptionRecord> page = new PageImpl<>(records, PageRequest.of(0, 10), 1);
            
            when(exceptionRecordRepository.findByStatusAndSeverity(
                    eq(ExceptionStatus.PENDING), eq(ExceptionSeverity.HIGH), any(PageRequest.class)))
                    .thenReturn(page);

            // When
            Page<ExceptionRecord> result = exceptionHandler.queryExceptions(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("获取流程实例异常应该返回正确结果")
        void getProcessInstanceExceptions_shouldReturnCorrectResults() {
            // Given
            List<ExceptionRecord> records = Arrays.asList(testRecord);
            when(exceptionRecordRepository.findByProcessInstanceIdOrderByOccurredTimeDesc("proc-123"))
                    .thenReturn(records);

            // When
            List<ExceptionRecord> result = exceptionHandler.getExceptionsByProcessInstanceId("proc-123");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProcessInstanceId()).isEqualTo("proc-123");
        }

        @Test
        @DisplayName("获取未解决异常应该返回正确结果")
        void getUnresolvedExceptions_shouldReturnCorrectResults() {
            // Given
            List<ExceptionRecord> records = Arrays.asList(testRecord);
            when(exceptionRecordRepository.findByResolvedFalseOrderBySeverityDescOccurredTimeDesc())
                    .thenReturn(records);

            // When
            List<ExceptionRecord> result = exceptionHandler.getUnresolvedExceptions();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getResolved()).isFalse();
        }
    }

    @Nested
    @DisplayName("异常统计测试")
    class ExceptionStatisticsTests {

        @Test
        @DisplayName("获取异常统计应该返回正确的统计信息")
        void getExceptionStatistics_shouldReturnCorrectStats() {
            // Given
            ExceptionRecord record1 = new ExceptionRecord();
            record1.setStatus(ExceptionStatus.PENDING);
            record1.setSeverity(ExceptionSeverity.CRITICAL);
            record1.setResolved(false);
            record1.setOccurredTime(LocalDateTime.now());
            record1.setExceptionType("DATABASE_ERROR");
            
            ExceptionRecord record2 = new ExceptionRecord();
            record2.setStatus(ExceptionStatus.RESOLVED);
            record2.setSeverity(ExceptionSeverity.HIGH);
            record2.setResolved(true);
            record2.setOccurredTime(LocalDateTime.now());
            record2.setExceptionType("NETWORK_ERROR");
            
            when(exceptionRecordRepository.findAll()).thenReturn(Arrays.asList(record1, record2));

            // When
            ExceptionStatisticsResult result = exceptionHandler.getExceptionStatistics();

            // Then
            assertThat(result.getTotalCount()).isEqualTo(2L);
            assertThat(result.getUnresolvedCount()).isEqualTo(1L);
            assertThat(result.getResolvedCount()).isEqualTo(1L);
            assertThat(result.getCriticalCount()).isEqualTo(1L);
            assertThat(result.getPendingCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("清理测试")
    class CleanupTests {

        @Test
        @DisplayName("清理已解决异常应该删除过期记录")
        void cleanupResolvedExceptions_shouldDeleteExpiredRecords() {
            // Given
            doNothing().when(exceptionRecordRepository).deleteByResolvedTrueAndResolvedTimeBefore(any());

            // When
            exceptionHandler.cleanupExpiredExceptions(30);

            // Then
            verify(exceptionRecordRepository).deleteByResolvedTrueAndResolvedTimeBefore(any());
        }
    }
}
