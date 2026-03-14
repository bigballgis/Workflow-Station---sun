package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.client.AdminCenterClient;
import com.workflow.dto.request.N8nActionRequest;
import com.workflow.dto.response.N8nExecutionResult;
import com.workflow.entity.N8nExecutionRecord;
import com.workflow.repository.N8nExecutionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * N8nTaskExecutor 单元测试
 * 测试 Service Task 执行流程、Webhook 调用成功/失败、重试机制
 * 需求: 4.1, 4.5, 4.6, 4.7, 4.8
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("N8nTaskExecutor Tests")
class N8nTaskExecutorTest {

    @Mock
    private AdminCenterClient adminCenterClient;

    @Mock
    private N8nExecutionRecordRepository executionRecordRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private N8nTaskExecutor executor;

    private static final String CALLBACK_BASE_URL = "http://workflow-engine:8081";
    private static final String WEBHOOK_URL = "https://n8n.example.com/webhook/test-wf";
    private static final String CONFIG_ID = "config-001";
    private static final String API_KEY = "test-api-key-123";

    @BeforeEach
    void setUp() {
        executor = new N8nTaskExecutor(
                adminCenterClient,
                executionRecordRepository,
                stringRedisTemplate,
                restTemplate,
                objectMapper
        );
        ReflectionTestUtils.setField(executor, "callbackBaseUrl", CALLBACK_BASE_URL);
    }

    // ==================== executeSynchronous Tests ====================

    @Nested
    @DisplayName("executeSynchronous Tests")
    class ExecuteSynchronousTests {

        @Test
        @DisplayName("Successful execution with output data")
        void successfulExecutionWithOutputData() {
            // Arrange
            N8nActionRequest request = buildActionRequest();

            Map<String, Object> n8nConfig = Map.of("apiKey", API_KEY);
            when(adminCenterClient.getN8nConfig(CONFIG_ID)).thenReturn(n8nConfig);

            Map<String, Object> responseBody = Map.of("result", "ok", "count", 42);
            ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
            when(restTemplate.exchange(eq(WEBHOOK_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> {
                        N8nExecutionRecord record = invocation.getArgument(0);
                        record.setId(1L);
                        return record;
                    });

            // Act
            N8nExecutionResult result = executor.executeSynchronous(request);

            // Assert
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            assertThat(result.getExecutionRecordId()).isEqualTo(1L);
            assertThat(result.getOutputData()).containsEntry("result", "ok");
            assertThat(result.getOutputData()).containsEntry("count", 42);

            // Verify record saved twice: once on creation (PENDING), once on success
            verify(executionRecordRepository, times(2)).save(any(N8nExecutionRecord.class));
        }

        @Test
        @DisplayName("N8N config not found returns failure")
        void configNotFoundReturnsFailure() {
            // Arrange
            N8nActionRequest request = buildActionRequest();
            when(adminCenterClient.getN8nConfig(CONFIG_ID)).thenReturn(null);

            // Act
            N8nExecutionResult result = executor.executeSynchronous(request);

            // Assert
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatus()).isEqualTo("FAILED");
            assertThat(result.getErrorMessage()).contains("Failed to retrieve N8N config");

            // Verify no webhook call was made
            verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), eq(Map.class));
        }

        @Test
        @DisplayName("Webhook returns non-2xx status")
        void webhookReturnsNon2xxStatus() {
            // Arrange
            N8nActionRequest request = buildActionRequest();

            Map<String, Object> n8nConfig = Map.of("apiKey", API_KEY);
            when(adminCenterClient.getN8nConfig(CONFIG_ID)).thenReturn(n8nConfig);

            ResponseEntity<Map> response = new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
            when(restTemplate.exchange(eq(WEBHOOK_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> {
                        N8nExecutionRecord record = invocation.getArgument(0);
                        record.setId(2L);
                        return record;
                    });

            // Act
            N8nExecutionResult result = executor.executeSynchronous(request);

            // Assert
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatus()).isEqualTo("FAILED");
            assertThat(result.getErrorMessage()).contains("N8N webhook returned HTTP");
            assertThat(result.getExecutionRecordId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Webhook throws exception")
        void webhookThrowsException() {
            // Arrange
            N8nActionRequest request = buildActionRequest();

            Map<String, Object> n8nConfig = Map.of("apiKey", API_KEY);
            when(adminCenterClient.getN8nConfig(CONFIG_ID)).thenReturn(n8nConfig);

            when(restTemplate.exchange(eq(WEBHOOK_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> {
                        N8nExecutionRecord record = invocation.getArgument(0);
                        record.setId(3L);
                        return record;
                    });

            // Act
            N8nExecutionResult result = executor.executeSynchronous(request);

            // Assert
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatus()).isEqualTo("FAILED");
            assertThat(result.getErrorMessage()).contains("Connection refused");
            assertThat(result.getExecutionRecordId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("Execution with input/output mapping")
        void executionWithInputOutputMapping() {
            // Arrange
            N8nActionRequest request = buildActionRequest();
            request.setInputData(Map.of("userName", "Alice", "age", 30));
            request.setInputMapping("[{\"source\":\"userName\",\"target\":\"name\"},{\"source\":\"age\",\"target\":\"userAge\"}]");
            request.setOutputMapping("[{\"source\":\"result\",\"target\":\"processResult\"}]");

            Map<String, Object> n8nConfig = Map.of("apiKey", API_KEY);
            when(adminCenterClient.getN8nConfig(CONFIG_ID)).thenReturn(n8nConfig);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("result", "approved");
            responseBody.put("extra", "ignored");
            ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
            when(restTemplate.exchange(eq(WEBHOOK_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> {
                        N8nExecutionRecord record = invocation.getArgument(0);
                        record.setId(4L);
                        return record;
                    });

            // Act
            N8nExecutionResult result = executor.executeSynchronous(request);

            // Assert
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            // Output mapping should map "result" -> "processResult"
            assertThat(result.getOutputData()).containsEntry("processResult", "approved");
            // "extra" should not be in the mapped output
            assertThat(result.getOutputData()).doesNotContainKey("extra");
        }
    }

    // ==================== generateCallbackToken Tests ====================

    @Nested
    @DisplayName("generateCallbackToken Tests")
    class GenerateCallbackTokenTests {

        @Test
        @DisplayName("Returns non-null UUID format string")
        void returnsNonNullUuidFormat() {
            String token = executor.generateCallbackToken();

            assertThat(token).isNotNull().isNotBlank();
            // Verify UUID format (8-4-4-4-12 hex digits)
            assertThat(token).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            // Verify it can be parsed as UUID
            assertThatCode(() -> UUID.fromString(token)).doesNotThrowAnyException();
        }
    }

    // ==================== buildCallbackUrl Tests ====================

    @Nested
    @DisplayName("buildCallbackUrl Tests")
    class BuildCallbackUrlTests {

        @Test
        @DisplayName("Returns correct URL format")
        void returnsCorrectUrlFormat() {
            String url = executor.buildCallbackUrl();

            assertThat(url).isEqualTo(CALLBACK_BASE_URL + "/api/workflow/n8n/callback");
        }
    }

    // ==================== calculateRetryDelay Tests ====================

    @Nested
    @DisplayName("calculateRetryDelay Tests")
    class CalculateRetryDelayTests {

        @Test
        @DisplayName("Returns correct exponential values")
        void returnsCorrectExponentialValues() {
            // delay = 1000 * 2^attempt
            assertThat(executor.calculateRetryDelay(0)).isEqualTo(1000L);   // 1000 * 2^0 = 1000
            assertThat(executor.calculateRetryDelay(1)).isEqualTo(2000L);   // 1000 * 2^1 = 2000
            assertThat(executor.calculateRetryDelay(2)).isEqualTo(4000L);   // 1000 * 2^2 = 4000
            assertThat(executor.calculateRetryDelay(3)).isEqualTo(8000L);   // 1000 * 2^3 = 8000
            assertThat(executor.calculateRetryDelay(4)).isEqualTo(16000L);  // 1000 * 2^4 = 16000
        }
    }

    // ==================== buildWebhookRequestBody Tests ====================

    @Nested
    @DisplayName("buildWebhookRequestBody Tests")
    class BuildWebhookRequestBodyTests {

        @Test
        @DisplayName("Contains all required fields")
        void containsAllRequiredFields() {
            Map<String, Object> inputData = Map.of("key1", "value1", "key2", 123);
            String callbackUrl = "http://localhost:8081/api/workflow/n8n/callback";
            String callbackToken = "test-token-uuid";

            Map<String, Object> body = executor.buildWebhookRequestBody(inputData, callbackUrl, callbackToken);

            assertThat(body).hasSize(3);
            assertThat(body).containsEntry("inputData", inputData);
            assertThat(body).containsEntry("callbackUrl", callbackUrl);
            assertThat(body).containsEntry("callbackToken", callbackToken);
        }
    }

    // ==================== Helper Methods ====================

    private N8nActionRequest buildActionRequest() {
        N8nActionRequest request = new N8nActionRequest();
        request.setN8nConfigId(CONFIG_ID);
        request.setWebhookUrl(WEBHOOK_URL);
        request.setN8nWorkflowId("wf-001");
        request.setTimeoutSeconds(120);
        request.setProcessInstanceId("proc-001");
        request.setTaskId("task-001");
        request.setInputData(Map.of("param1", "value1"));
        return request;
    }
}
