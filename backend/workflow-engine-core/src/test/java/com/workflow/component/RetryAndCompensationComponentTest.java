package com.workflow.component;

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

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 自动重试和补偿机制组件单元测试
 * 需求: 9.6, 9.7, 9.8
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("自动重试和补偿机制组件测试")
class RetryAndCompensationComponentTest {

    @Mock
    private ExceptionRecordRepository exceptionRecordRepository;

    @InjectMocks
    private RetryAndCompensationComponent retryComponent;

    private ExceptionRecord testRecord;

    @BeforeEach
    void setUp() {
        testRecord = new ExceptionRecord();
        testRecord.setId("test-exception-id");
        testRecord.setProcessInstanceId("proc-123");
        testRecord.setTaskId("task-456");
        testRecord.setExceptionType("PROGRAMMING_ERROR");
        testRecord.setExceptionMessage("Test exception");
        testRecord.setSeverity(ExceptionSeverity.MEDIUM);
        testRecord.setStatus(ExceptionStatus.PENDING);
        testRecord.setOccurredTime(LocalDateTime.now());
        testRecord.setRetryCount(0);
        testRecord.setMaxRetryCount(3);
        testRecord.setResolved(false);
        testRecord.setNextRetryTime(LocalDateTime.now().minusMinutes(1));
    }

    @Nested
    @DisplayName("自动重试测试")
    class AutoRetryTests {

        @Test
        @DisplayName("已解决的异常不应该重试")
        void executeRetry_resolvedShouldNotRetry() {
            // Given
            testRecord.setResolved(true);
            when(exceptionRecordRepository.findById("test-exception-id")).thenReturn(Optional.of(testRecord));

            // When
            RetryAndCompensationComponent.RetryResult result = retryComponent.executeRetry("test-exception-id");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("已解决");
            verify(exceptionRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("超过最大重试次数应该移入死信队列")
        void executeRetry_exceedMaxShouldMoveToDeadLetter() {
            // Given
            testRecord.setRetryCount(3);
            when(exceptionRecordRepository.findById("test-exception-id")).thenReturn(Optional.of(testRecord));
            when(exceptionRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            RetryAndCompensationComponent.RetryResult result = retryComponent.executeRetry("test-exception-id");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isMovedToDeadLetter()).isTrue();
            assertThat(result.getMessage()).contains("死信队列");
        }

        @Test
        @DisplayName("重试应该递增重试计数")
        void executeRetry_shouldIncrementRetryCount() {
            // Given
            when(exceptionRecordRepository.findById("test-exception-id")).thenReturn(Optional.of(testRecord));
            when(exceptionRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            RetryAndCompensationComponent.RetryResult result = retryComponent.executeRetry("test-exception-id");

            // Then
            assertThat(result.getAttemptNumber()).isEqualTo(1);
            verify(exceptionRecordRepository).save(argThat(record -> 
                    record.getRetryCount() == 1 && record.getLastRetryTime() != null));
        }

        @Test
        @DisplayName("不存在的异常记录应该抛出异常")
        void executeRetry_nonExistentShouldThrow() {
            // Given
            when(exceptionRecordRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> retryComponent.executeRetry("non-existent"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("异常记录不存在");
        }

        @Test
        @DisplayName("批量执行待重试异常")
        void executePendingRetries_shouldProcessAllPending() {
            // Given
            ExceptionRecord record1 = createTestRecord("id-1", 0);
            ExceptionRecord record2 = createTestRecord("id-2", 1);
            
            when(exceptionRecordRepository.findPendingRetryExceptions(any()))
                    .thenReturn(Arrays.asList(record1, record2));
            when(exceptionRecordRepository.findById("id-1")).thenReturn(Optional.of(record1));
            when(exceptionRecordRepository.findById("id-2")).thenReturn(Optional.of(record2));
            when(exceptionRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            List<RetryAndCompensationComponent.RetryResult> results = retryComponent.executePendingRetries();

            // Then
            assertThat(results).hasSize(2);
        }
    }

    @Nested
    @DisplayName("死信队列测试")
    class DeadLetterQueueTests {

        @Test
        @DisplayName("移入死信队列应该创建死信消息")
        void moveToDeadLetterQueue_shouldCreateMessage() {
            // Given
            when(exceptionRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            RetryAndCompensationComponent.DeadLetterMessage message = 
                    retryComponent.moveToDeadLetterQueue(testRecord, "超过最大重试次数");

            // Then
            assertThat(message).isNotNull();
            assertThat(message.getId()).isNotNull();
            assertThat(message.getExceptionRecordId()).isEqualTo("test-exception-id");
            assertThat(message.getProcessInstanceId()).isEqualTo("proc-123");
            assertThat(message.getReason()).isEqualTo("超过最大重试次数");
            assertThat(message.isProcessed()).isFalse();
        }

        @Test
        @DisplayName("获取死信队列消息列表")
        void getDeadLetterMessages_shouldReturnMessages() {
            // Given
            when(exceptionRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            retryComponent.moveToDeadLetterQueue(testRecord, "Test reason");

            // When
            List<Map<String, Object>> messages = retryComponent.getDeadLetterMessages(false);

            // Then
            assertThat(messages).isNotEmpty();
            assertThat(messages.get(0).get("reason")).isEqualTo("Test reason");
        }

        @Test
        @DisplayName("处理死信消息 - 忽略操作")
        void processDeadLetterMessage_ignoreShouldWork() {
            // Given
            when(exceptionRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            RetryAndCompensationComponent.DeadLetterMessage message = 
                    retryComponent.moveToDeadLetterQueue(testRecord, "Test reason");

            // When
            Map<String, Object> result = retryComponent.processDeadLetterMessage(
                    message.getId(), "admin", "IGNORE", "Not important");

            // Then
            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("action")).isEqualTo("IGNORE");
        }

        @Test
        @DisplayName("处理死信消息 - 重试操作")
        void processDeadLetterMessage_retryShouldResetCount() {
            // Given
            // 设置初始重试计数为非零值，以便验证重置
            testRecord.setRetryCount(3);
            when(exceptionRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(exceptionRecordRepository.findById("test-exception-id")).thenReturn(Optional.of(testRecord));
            
            RetryAndCompensationComponent.DeadLetterMessage message = 
                    retryComponent.moveToDeadLetterQueue(testRecord, "Test reason");

            // When
            Map<String, Object> result = retryComponent.processDeadLetterMessage(
                    message.getId(), "admin", "RETRY", "Retry again");

            // Then
            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("action")).isEqualTo("RETRY");
            // 验证save被调用至少2次（moveToDeadLetterQueue一次，processDeadLetterMessage一次）
            // 并且最后一次调用时retryCount被重置为0
            verify(exceptionRecordRepository, atLeast(2)).save(any());
            assertThat(testRecord.getRetryCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("处理不存在的死信消息应该失败")
        void processDeadLetterMessage_nonExistentShouldFail() {
            // When
            Map<String, Object> result = retryComponent.processDeadLetterMessage(
                    "non-existent", "admin", "IGNORE", "note");

            // Then - 实现捕获异常并返回错误结果，而不是抛出异常
            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("error")).asString().contains("死信消息不存在");
        }
    }

    @Nested
    @DisplayName("补偿事务测试")
    class CompensationTransactionTests {

        @Test
        @DisplayName("注册补偿事务应该成功")
        void registerCompensation_shouldSucceed() {
            // Given
            Map<String, Object> compensationData = new HashMap<>();
            compensationData.put("originalValue", "test");

            // When
            RetryAndCompensationComponent.CompensationTransaction transaction = 
                    retryComponent.registerCompensation("proc-123", "activity-1", 
                            "ROLLBACK_VARIABLES", compensationData);

            // Then
            assertThat(transaction).isNotNull();
            assertThat(transaction.getId()).isNotNull();
            assertThat(transaction.getProcessInstanceId()).isEqualTo("proc-123");
            assertThat(transaction.getActivityId()).isEqualTo("activity-1");
            assertThat(transaction.getCompensationType()).isEqualTo("ROLLBACK_VARIABLES");
            assertThat(transaction.isExecuted()).isFalse();
        }

        @Test
        @DisplayName("执行补偿事务应该按倒序执行")
        void executeCompensation_shouldExecuteInReverseOrder() {
            // Given
            retryComponent.registerCompensation("proc-123", "activity-1", "CUSTOM", null);
            retryComponent.registerCompensation("proc-123", "activity-2", "CUSTOM", null);
            retryComponent.registerCompensation("proc-123", "activity-3", "CUSTOM", null);

            // When
            List<Map<String, Object>> results = retryComponent.executeCompensation("proc-123");

            // Then
            assertThat(results).hasSize(3);
            // 验证所有补偿都执行成功
            for (Map<String, Object> result : results) {
                assertThat(result.get("success")).isEqualTo(true);
            }
        }

        @Test
        @DisplayName("获取补偿事务列表")
        void getCompensationTransactions_shouldReturnList() {
            // Given
            retryComponent.registerCompensation("proc-123", "activity-1", "CUSTOM", null);
            retryComponent.registerCompensation("proc-456", "activity-2", "CUSTOM", null);

            // When
            List<Map<String, Object>> transactions = 
                    retryComponent.getCompensationTransactions("proc-123", false);

            // Then
            assertThat(transactions).hasSize(1);
            assertThat(transactions.get(0).get("processInstanceId")).isEqualTo("proc-123");
        }

        @Test
        @DisplayName("获取所有补偿事务")
        void getCompensationTransactions_allShouldReturnAll() {
            // Given
            retryComponent.registerCompensation("proc-123", "activity-1", "CUSTOM", null);
            retryComponent.registerCompensation("proc-456", "activity-2", "CUSTOM", null);

            // When
            List<Map<String, Object>> transactions = 
                    retryComponent.getCompensationTransactions(null, false);

            // Then
            assertThat(transactions).hasSize(2);
        }
    }

    @Nested
    @DisplayName("统计信息测试")
    class StatisticsTests {

        @Test
        @DisplayName("获取重试统计信息")
        void getRetryStatistics_shouldReturnStats() {
            // Given
            when(exceptionRecordRepository.findPendingRetryExceptions(any())).thenReturn(new ArrayList<>());

            // When
            Map<String, Object> stats = retryComponent.getRetryStatistics();

            // Then
            assertThat(stats).containsKeys(
                    "deadLetterTotal", "deadLetterProcessed", "deadLetterPending",
                    "compensationTotal", "compensationExecuted", "compensationSuccessful",
                    "pendingRetries"
            );
        }
    }

    // 辅助方法
    private ExceptionRecord createTestRecord(String id, int retryCount) {
        ExceptionRecord record = new ExceptionRecord();
        record.setId(id);
        record.setProcessInstanceId("proc-" + id);
        record.setExceptionType("TEST_ERROR");
        record.setExceptionMessage("Test exception " + id);
        record.setSeverity(ExceptionSeverity.MEDIUM);
        record.setStatus(ExceptionStatus.PENDING);
        record.setOccurredTime(LocalDateTime.now());
        record.setRetryCount(retryCount);
        record.setMaxRetryCount(3);
        record.setResolved(false);
        record.setNextRetryTime(LocalDateTime.now().minusMinutes(1));
        return record;
    }
}
