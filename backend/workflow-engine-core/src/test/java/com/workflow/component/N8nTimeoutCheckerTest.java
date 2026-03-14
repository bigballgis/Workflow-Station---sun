package com.workflow.component;

import com.workflow.entity.N8nExecutionRecord;
import com.workflow.repository.N8nExecutionRecordRepository;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * N8nTimeoutChecker 单元测试
 * 测试超时检测、状态更新、Flowable 异常处理触发
 * 需求: 6.2, 6.3, 6.5
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("N8nTimeoutChecker Tests")
class N8nTimeoutCheckerTest {

    @Mock
    private N8nExecutionRecordRepository executionRecordRepository;

    @Mock
    private RuntimeService runtimeService;

    private N8nTimeoutChecker timeoutChecker;

    @BeforeEach
    void setUp() {
        timeoutChecker = new N8nTimeoutChecker(executionRecordRepository, runtimeService);
    }

    // ==================== checkTimeouts Tests ====================

    @Nested
    @DisplayName("checkTimeouts Tests")
    class CheckTimeoutsTests {

        @Test
        @DisplayName("Finds and marks timed-out records")
        void findsAndMarksTimedOutRecords() {
            // Arrange: a record that started 600s ago with 300s timeout → timed out
            N8nExecutionRecord timedOutRecord = createRunningRecord(1L, "task-001",
                    Instant.now().minusSeconds(600), 300);

            when(executionRecordRepository.findByStatusAndStartedAtBefore(eq("RUNNING"), any(Instant.class)))
                    .thenReturn(List.of(timedOutRecord));

            // Act
            timeoutChecker.checkTimeouts();

            // Assert
            ArgumentCaptor<N8nExecutionRecord> captor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository).save(captor.capture());

            N8nExecutionRecord saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo("TIMEOUT");
            assertThat(saved.getErrorMessage()).contains("timed out");
            assertThat(saved.getCompletedAt()).isNotNull();

            verify(runtimeService).trigger("task-001");
        }

        @Test
        @DisplayName("Skips records that haven't timed out yet")
        void skipsRecordsThatHaventTimedOut() {
            // Arrange: a record that started 100s ago with 300s timeout → NOT timed out
            N8nExecutionRecord activeRecord = createRunningRecord(2L, "task-002",
                    Instant.now().minusSeconds(100), 300);

            when(executionRecordRepository.findByStatusAndStartedAtBefore(eq("RUNNING"), any(Instant.class)))
                    .thenReturn(List.of(activeRecord));

            // Act
            timeoutChecker.checkTimeouts();

            // Assert: no save, no trigger
            verify(executionRecordRepository, never()).save(any());
            verify(runtimeService, never()).trigger(anyString());
        }

        @Test
        @DisplayName("No RUNNING records found - does nothing")
        void noRunningRecordsFound() {
            when(executionRecordRepository.findByStatusAndStartedAtBefore(eq("RUNNING"), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            // Act
            timeoutChecker.checkTimeouts();

            // Assert
            verify(executionRecordRepository, never()).save(any());
            verify(runtimeService, never()).trigger(anyString());
        }
    }

    // ==================== isTimedOut Tests ====================

    @Nested
    @DisplayName("isTimedOut Tests")
    class IsTimedOutTests {

        @Test
        @DisplayName("Returns true when past deadline")
        void returnsTrueWhenPastDeadline() {
            Instant startedAt = Instant.now().minusSeconds(400);
            N8nExecutionRecord record = createRunningRecord(1L, "task-001", startedAt, 300);

            Instant now = Instant.now();
            assertThat(timeoutChecker.isTimedOut(record, now)).isTrue();
        }

        @Test
        @DisplayName("Returns false when before deadline")
        void returnsFalseWhenBeforeDeadline() {
            Instant startedAt = Instant.now().minusSeconds(100);
            N8nExecutionRecord record = createRunningRecord(2L, "task-002", startedAt, 300);

            Instant now = Instant.now();
            assertThat(timeoutChecker.isTimedOut(record, now)).isFalse();
        }

        @Test
        @DisplayName("Returns false when startedAt is null")
        void returnsFalseWhenStartedAtIsNull() {
            N8nExecutionRecord record = createRunningRecord(3L, "task-003", null, 300);

            assertThat(timeoutChecker.isTimedOut(record, Instant.now())).isFalse();
        }

        @Test
        @DisplayName("Returns false when timeoutSeconds is null")
        void returnsFalseWhenTimeoutSecondsIsNull() {
            N8nExecutionRecord record = createRunningRecord(4L, "task-004",
                    Instant.now().minusSeconds(100), null);

            assertThat(timeoutChecker.isTimedOut(record, Instant.now())).isFalse();
        }
    }

    // ==================== handleTimeout Tests ====================

    @Nested
    @DisplayName("handleTimeout (via checkTimeouts) Tests")
    class HandleTimeoutTests {

        @Test
        @DisplayName("Triggers Flowable error handling for timed-out record")
        void triggersFlowableErrorHandling() {
            N8nExecutionRecord record = createRunningRecord(5L, "task-005",
                    Instant.now().minusSeconds(500), 300);

            when(executionRecordRepository.findByStatusAndStartedAtBefore(eq("RUNNING"), any(Instant.class)))
                    .thenReturn(List.of(record));

            timeoutChecker.checkTimeouts();

            verify(runtimeService).trigger("task-005");
        }

        @Test
        @DisplayName("Handles Flowable trigger failure gracefully (best effort)")
        void handlesFlowableTriggerFailureGracefully() {
            N8nExecutionRecord record = createRunningRecord(6L, "task-006",
                    Instant.now().minusSeconds(500), 300);

            when(executionRecordRepository.findByStatusAndStartedAtBefore(eq("RUNNING"), any(Instant.class)))
                    .thenReturn(List.of(record));

            doThrow(new RuntimeException("Flowable engine error"))
                    .when(runtimeService).trigger("task-006");

            // Act - should not throw
            assertThatCode(() -> timeoutChecker.checkTimeouts()).doesNotThrowAnyException();

            // Record should still be saved as TIMEOUT
            ArgumentCaptor<N8nExecutionRecord> captor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("TIMEOUT");
        }
    }

    // ==================== Helper Methods ====================

    private N8nExecutionRecord createRunningRecord(Long id, String taskId,
                                                    Instant startedAt, Integer timeoutSeconds) {
        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setId(id);
        record.setTaskId(taskId);
        record.setProcessInstanceId("proc-" + id);
        record.setStatus("RUNNING");
        record.setSourceType("SERVICE_TASK");
        record.setStartedAt(startedAt);
        record.setTimeoutSeconds(timeoutSeconds);
        return record;
    }
}
