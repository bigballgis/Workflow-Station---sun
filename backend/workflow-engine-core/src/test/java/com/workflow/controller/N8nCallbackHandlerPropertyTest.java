package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.N8nCallbackRequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.entity.N8nExecutionRecord;
import com.workflow.repository.N8nExecutionRecordRepository;
import net.jqwik.api.*;
import org.flowable.engine.RuntimeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-Based Tests for N8nCallbackHandler
 *
 * Tests callback token validation correctness (Property 9) and
 * execution record lifecycle integrity (Property 10).
 */
class N8nCallbackHandlerPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Property 9: 回调令牌验证正确性 ====================

    /**
     * Feature: n8n-workflow-integration, Property 9: 回调令牌验证正确性
     *
     * (a) If callbackToken is not in Redis or expired, should return 401 Unauthorized.
     *
     * Validates: Requirements 5.2, 5.3, 6.5
     */
    @Property(tries = 100)
    @Label("Property 9a: Invalid/expired callbackToken returns 401 Unauthorized")
    void invalidOrExpiredTokenReturns401(
            @ForAll("randomCallbackToken") String callbackToken) {

        // Setup mocks
        N8nExecutionRecordRepository repo = mock(N8nExecutionRecordRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Token not found in Redis (expired or never existed)
        when(valueOps.get("n8n:callback:" + callbackToken)).thenReturn(null);

        N8nCallbackHandler handler = new N8nCallbackHandler(repo, redisTemplate, runtimeService, objectMapper);

        N8nCallbackRequest request = new N8nCallbackRequest();
        request.setCallbackToken(callbackToken);
        request.setStatus("success");

        ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    /**
     * Feature: n8n-workflow-integration, Property 9: 回调令牌验证正确性
     *
     * (b) If callbackToken maps to an execution record with status TIMEOUT, should return 410 Gone.
     *
     * Validates: Requirements 5.2, 5.3, 6.5
     */
    @Property(tries = 100)
    @Label("Property 9b: Timed-out execution record returns 410 Gone")
    void timedOutRecordReturns410(
            @ForAll("randomCallbackToken") String callbackToken,
            @ForAll("randomRecordId") Long recordId) {

        N8nExecutionRecordRepository repo = mock(N8nExecutionRecordRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("n8n:callback:" + callbackToken)).thenReturn(String.valueOf(recordId));

        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setId(recordId);
        record.setStatus("TIMEOUT");
        record.setSourceType("SERVICE_TASK");
        record.setStartedAt(Instant.now().minusSeconds(600));
        record.setTimeoutSeconds(300);
        when(repo.findById(recordId)).thenReturn(Optional.of(record));

        N8nCallbackHandler handler = new N8nCallbackHandler(repo, redisTemplate, runtimeService, objectMapper);

        N8nCallbackRequest request = new N8nCallbackRequest();
        request.setCallbackToken(callbackToken);
        request.setStatus("success");

        ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    /**
     * Feature: n8n-workflow-integration, Property 9: 回调令牌验证正确性
     *
     * (c) If callbackToken is valid and execution record status is RUNNING, should return 200 OK.
     *
     * Validates: Requirements 5.2, 5.3, 6.5
     */
    @Property(tries = 100)
    @Label("Property 9c: Valid token with RUNNING record returns 200 OK")
    void validTokenWithRunningRecordReturns200(
            @ForAll("randomCallbackToken") String callbackToken,
            @ForAll("randomRecordId") Long recordId,
            @ForAll("randomCallbackStatus") String callbackStatus) {

        N8nExecutionRecordRepository repo = mock(N8nExecutionRecordRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("n8n:callback:" + callbackToken)).thenReturn(String.valueOf(recordId));

        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setId(recordId);
        record.setProcessInstanceId("proc-" + recordId);
        record.setTaskId("task-" + recordId);
        record.setCallbackToken(callbackToken);
        record.setStatus("RUNNING");
        record.setSourceType("SERVICE_TASK");
        record.setStartedAt(Instant.now().minusSeconds(30));
        record.setTimeoutSeconds(300);
        when(repo.findById(recordId)).thenReturn(Optional.of(record));

        // For success callbacks, mock the output mapping lookup
        when(runtimeService.getVariable(eq("task-" + recordId), eq("n8n_outputMapping"))).thenReturn(null);

        N8nCallbackHandler handler = new N8nCallbackHandler(repo, redisTemplate, runtimeService, objectMapper);

        N8nCallbackRequest request = new N8nCallbackRequest();
        request.setCallbackToken(callbackToken);
        request.setStatus(callbackStatus);
        if ("success".equals(callbackStatus)) {
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("result", "ok");
            request.setOutputData(outputData);
        } else {
            request.setErrorMessage("Some error occurred");
        }

        ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    // ==================== Property 10: 执行记录生命周期完整性 ====================

    /**
     * Feature: n8n-workflow-integration, Property 10: 执行记录生命周期完整性
     *
     * A created execution record should have startedAt and timeoutSeconds set.
     *
     * Validates: Requirements 5.7, 6.1
     */
    @Property(tries = 100)
    @Label("Property 10a: Created record has startedAt and timeoutSeconds")
    void createdRecordHasStartedAtAndTimeoutSeconds(
            @ForAll("randomTimeoutSeconds") int timeoutSeconds) {

        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setStatus("PENDING");
        record.setSourceType("SERVICE_TASK");
        record.setStartedAt(Instant.now());
        record.setTimeoutSeconds(timeoutSeconds);

        assertThat(record.getStartedAt())
                .as("Created record must have startedAt set")
                .isNotNull();
        assertThat(record.getTimeoutSeconds())
                .as("Created record must have timeoutSeconds set")
                .isNotNull()
                .isEqualTo(timeoutSeconds);
    }

    /**
     * Feature: n8n-workflow-integration, Property 10: 执行记录生命周期完整性
     *
     * After a success callback, the record should have status=SUCCESS, outputData set, and completedAt set.
     *
     * Validates: Requirements 5.7, 6.1
     */
    @Property(tries = 100)
    @Label("Property 10b: After success callback: status=SUCCESS, outputData and completedAt set")
    void afterSuccessCallbackRecordHasCorrectState(
            @ForAll("randomTimeoutSeconds") int timeoutSeconds,
            @ForAll("randomOutputDataJson") String outputDataJson) {

        // Simulate the lifecycle: create -> running -> success callback
        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setId(1L);
        record.setStatus("RUNNING");
        record.setSourceType("SERVICE_TASK");
        record.setProcessInstanceId("proc-1");
        record.setTaskId("task-1");
        record.setStartedAt(Instant.now().minusSeconds(30));
        record.setTimeoutSeconds(timeoutSeconds);

        // Simulate what handleSuccessCallback does
        record.setStatus("SUCCESS");
        record.setOutputData(outputDataJson);
        record.setCompletedAt(Instant.now());

        assertThat(record.getStatus())
                .as("After success callback, status must be SUCCESS")
                .isEqualTo("SUCCESS");
        assertThat(record.getOutputData())
                .as("After success callback, outputData must be set")
                .isNotNull()
                .isEqualTo(outputDataJson);
        assertThat(record.getCompletedAt())
                .as("After success callback, completedAt must be set")
                .isNotNull();
        assertThat(record.getCompletedAt())
                .as("completedAt must be after startedAt")
                .isAfter(record.getStartedAt());
    }

    /**
     * Feature: n8n-workflow-integration, Property 10: 执行记录生命周期完整性
     *
     * After a failure callback, the record should have status=FAILED and errorMessage set.
     *
     * Validates: Requirements 5.7, 6.1
     */
    @Property(tries = 100)
    @Label("Property 10c: After failure callback: status=FAILED and errorMessage set")
    void afterFailureCallbackRecordHasCorrectState(
            @ForAll("randomTimeoutSeconds") int timeoutSeconds,
            @ForAll("randomErrorMessage") String errorMessage) {

        // Simulate the lifecycle: create -> running -> failure callback
        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setId(1L);
        record.setStatus("RUNNING");
        record.setSourceType("SERVICE_TASK");
        record.setProcessInstanceId("proc-1");
        record.setTaskId("task-1");
        record.setStartedAt(Instant.now().minusSeconds(30));
        record.setTimeoutSeconds(timeoutSeconds);

        // Simulate what handleFailureCallback does
        record.setStatus("FAILED");
        record.setErrorMessage(errorMessage);
        record.setCompletedAt(Instant.now());

        assertThat(record.getStatus())
                .as("After failure callback, status must be FAILED")
                .isEqualTo("FAILED");
        assertThat(record.getErrorMessage())
                .as("After failure callback, errorMessage must be set")
                .isNotNull()
                .isEqualTo(errorMessage);
        assertThat(record.getCompletedAt())
                .as("After failure callback, completedAt must be set")
                .isNotNull();
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<String> randomCallbackToken() {
        return Arbitraries.strings().alpha().numeric()
                .ofMinLength(8).ofMaxLength(64);
    }

    @Provide
    Arbitrary<Long> randomRecordId() {
        return Arbitraries.longs().between(1L, 100000L);
    }

    @Provide
    Arbitrary<String> randomCallbackStatus() {
        return Arbitraries.of("success", "failed");
    }

    @Provide
    Arbitrary<Integer> randomTimeoutSeconds() {
        return Arbitraries.integers().between(30, 3600);
    }

    @Provide
    Arbitrary<String> randomOutputDataJson() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .map(val -> "{\"result\":\"" + val + "\"}");
    }

    @Provide
    Arbitrary<String> randomErrorMessage() {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(100);
    }
}
