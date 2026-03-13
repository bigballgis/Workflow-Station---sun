package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.N8nCallbackRequest;
import com.workflow.dto.response.ApiResponse;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * N8nCallbackHandler 单元测试
 * 测试回调处理成功/失败、Token 验证、输出变量写入
 * 需求: 5.1, 5.2, 5.3, 5.6, 6.5
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("N8nCallbackHandler Tests")
class N8nCallbackHandlerTest {

    @Mock
    private N8nExecutionRecordRepository executionRecordRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private N8nCallbackHandler handler;

    private static final String VALID_TOKEN = "valid-callback-token-123";
    private static final Long RECORD_ID = 42L;
    private static final String PROCESS_INSTANCE_ID = "proc-inst-001";
    private static final String TASK_ID = "exec-001";

    @BeforeEach
    void setUp() {
        handler = new N8nCallbackHandler(
                executionRecordRepository,
                stringRedisTemplate,
                runtimeService,
                objectMapper
        );
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private N8nExecutionRecord buildRunningRecord() {
        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setId(RECORD_ID);
        record.setProcessInstanceId(PROCESS_INSTANCE_ID);
        record.setTaskId(TASK_ID);
        record.setCallbackToken(VALID_TOKEN);
        record.setStatus("RUNNING");
        record.setSourceType("SERVICE_TASK");
        record.setStartedAt(Instant.now().minusSeconds(60));
        record.setTimeoutSeconds(300);
        return record;
    }

    private N8nCallbackRequest buildSuccessRequest() {
        N8nCallbackRequest request = new N8nCallbackRequest();
        request.setCallbackToken(VALID_TOKEN);
        request.setStatus("success");
        Map<String, Object> outputData = new HashMap<>();
        outputData.put("result", "approved");
        outputData.put("score", 95);
        request.setOutputData(outputData);
        return request;
    }

    private N8nCallbackRequest buildFailureRequest() {
        N8nCallbackRequest request = new N8nCallbackRequest();
        request.setCallbackToken(VALID_TOKEN);
        request.setStatus("failed");
        request.setErrorMessage("N8N workflow execution error: timeout");
        return request;
    }

    // ==================== Token Validation Tests ====================

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should return 401 when callbackToken is null")
        void shouldReturn401WhenTokenIsNull() {
            N8nCallbackRequest request = new N8nCallbackRequest();
            request.setCallbackToken(null);
            request.setStatus("success");

            ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("Should return 401 when callbackToken is blank")
        void shouldReturn401WhenTokenIsBlank() {
            N8nCallbackRequest request = new N8nCallbackRequest();
            request.setCallbackToken("   ");
            request.setStatus("success");

            ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Should return 401 when callbackToken not found in Redis")
        void shouldReturn401WhenTokenNotInRedis() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(null);

            N8nCallbackRequest request = buildSuccessRequest();
            ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().getCode()).isEqualTo("UNAUTHORIZED");
        }

        @Test
        @DisplayName("Should return 401 when execution record not found")
        void shouldReturn401WhenRecordNotFound() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(String.valueOf(RECORD_ID));
            when(executionRecordRepository.findById(RECORD_ID)).thenReturn(Optional.empty());

            N8nCallbackRequest request = buildSuccessRequest();
            ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(stringRedisTemplate).delete("n8n:callback:" + VALID_TOKEN);
        }

        @Test
        @DisplayName("Should return 410 when task has timed out")
        void shouldReturn410WhenTaskTimedOut() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(String.valueOf(RECORD_ID));
            N8nExecutionRecord record = buildRunningRecord();
            record.setStatus("TIMEOUT");
            when(executionRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));

            N8nCallbackRequest request = buildSuccessRequest();
            ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
            assertThat(response.getBody().getCode()).isEqualTo("GONE");
            verify(stringRedisTemplate).delete("n8n:callback:" + VALID_TOKEN);
        }
    }

    // ==================== Success Callback Tests ====================

    @Nested
    @DisplayName("Success Callback Tests")
    class SuccessCallbackTests {

        @Test
        @DisplayName("Should process success callback and update record")
        void shouldProcessSuccessCallback() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(String.valueOf(RECORD_ID));
            N8nExecutionRecord record = buildRunningRecord();
            when(executionRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));
            when(runtimeService.getVariable(TASK_ID, "n8n_outputMapping")).thenReturn(null);

            N8nCallbackRequest request = buildSuccessRequest();
            ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();

            // Verify record updated
            ArgumentCaptor<N8nExecutionRecord> captor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository, atLeastOnce()).save(captor.capture());
            N8nExecutionRecord saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo("SUCCESS");
            assertThat(saved.getCompletedAt()).isNotNull();
            assertThat(saved.getOutputData()).isNotNull();
        }

        @Test
        @DisplayName("Should apply output mapping when available")
        void shouldApplyOutputMapping() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(String.valueOf(RECORD_ID));
            N8nExecutionRecord record = buildRunningRecord();
            when(executionRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));

            String outputMapping = "[{\"source\":\"result\",\"target\":\"approvalResult\"}]";
            when(runtimeService.getVariable(TASK_ID, "n8n_outputMapping")).thenReturn(outputMapping);

            N8nCallbackRequest request = buildSuccessRequest();
            ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Verify variables were set
            ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(runtimeService).setVariables(eq(TASK_ID), varsCaptor.capture());
            Map<String, Object> mappedVars = varsCaptor.getValue();
            assertThat(mappedVars).containsEntry("approvalResult", "approved");
        }

        @Test
        @DisplayName("Should trigger process continuation on success")
        void shouldTriggerProcessContinuation() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(String.valueOf(RECORD_ID));
            N8nExecutionRecord record = buildRunningRecord();
            when(executionRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));
            when(runtimeService.getVariable(TASK_ID, "n8n_outputMapping")).thenReturn(null);

            N8nCallbackRequest request = buildSuccessRequest();
            handler.handleCallback(request);

            verify(runtimeService).trigger(TASK_ID);
        }

        @Test
        @DisplayName("Should delete Redis key after success callback")
        void shouldDeleteRedisKeyAfterSuccess() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(String.valueOf(RECORD_ID));
            N8nExecutionRecord record = buildRunningRecord();
            when(executionRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));
            when(runtimeService.getVariable(TASK_ID, "n8n_outputMapping")).thenReturn(null);

            N8nCallbackRequest request = buildSuccessRequest();
            handler.handleCallback(request);

            verify(stringRedisTemplate).delete("n8n:callback:" + VALID_TOKEN);
        }
    }

    // ==================== Failure Callback Tests ====================

    @Nested
    @DisplayName("Failure Callback Tests")
    class FailureCallbackTests {

        @Test
        @DisplayName("Should process failure callback and mark FAILED")
        void shouldProcessFailureCallback() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(String.valueOf(RECORD_ID));
            N8nExecutionRecord record = buildRunningRecord();
            when(executionRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));

            N8nCallbackRequest request = buildFailureRequest();
            ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            ArgumentCaptor<N8nExecutionRecord> captor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository, atLeastOnce()).save(captor.capture());
            N8nExecutionRecord saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo("FAILED");
            assertThat(saved.getErrorMessage()).contains("timeout");
            assertThat(saved.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should set default error message when none provided")
        void shouldSetDefaultErrorMessage() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(String.valueOf(RECORD_ID));
            N8nExecutionRecord record = buildRunningRecord();
            when(executionRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));

            N8nCallbackRequest request = new N8nCallbackRequest();
            request.setCallbackToken(VALID_TOKEN);
            request.setStatus("failed");
            request.setErrorMessage(null);

            handler.handleCallback(request);

            ArgumentCaptor<N8nExecutionRecord> captor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository, atLeastOnce()).save(captor.capture());
            N8nExecutionRecord saved = captor.getValue();
            assertThat(saved.getErrorMessage()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("Should trigger error handling on failure")
        void shouldTriggerErrorHandling() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(String.valueOf(RECORD_ID));
            N8nExecutionRecord record = buildRunningRecord();
            when(executionRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));

            N8nCallbackRequest request = buildFailureRequest();
            handler.handleCallback(request);

            verify(runtimeService).trigger(TASK_ID);
        }

        @Test
        @DisplayName("Should delete Redis key after failure callback")
        void shouldDeleteRedisKeyAfterFailure() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(String.valueOf(RECORD_ID));
            N8nExecutionRecord record = buildRunningRecord();
            when(executionRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));

            N8nCallbackRequest request = buildFailureRequest();
            handler.handleCallback(request);

            verify(stringRedisTemplate).delete("n8n:callback:" + VALID_TOKEN);
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should mark FAILED when output mapping throws exception")
        void shouldMarkFailedWhenOutputMappingFails() {
            when(valueOperations.get("n8n:callback:" + VALID_TOKEN)).thenReturn(String.valueOf(RECORD_ID));
            N8nExecutionRecord record = buildRunningRecord();
            when(executionRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));

            // Invalid JSON for output mapping
            when(runtimeService.getVariable(TASK_ID, "n8n_outputMapping")).thenReturn("invalid-json");

            N8nCallbackRequest request = buildSuccessRequest();
            ResponseEntity<ApiResponse<Void>> response = handler.handleCallback(request);

            // Should still return 200 but record should be FAILED
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            ArgumentCaptor<N8nExecutionRecord> captor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository, atLeastOnce()).save(captor.capture());
            N8nExecutionRecord saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo("FAILED");
            assertThat(saved.getErrorMessage()).contains("Callback processing error");
        }
    }
}
