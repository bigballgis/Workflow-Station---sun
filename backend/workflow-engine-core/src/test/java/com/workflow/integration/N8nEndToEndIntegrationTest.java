package com.workflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.client.AdminCenterClient;
import com.workflow.component.N8nTaskExecutor;
import com.workflow.component.N8nTimeoutChecker;
import com.workflow.controller.N8nCallbackHandler;
import com.workflow.controller.N8nExecutionController;
import com.workflow.dto.request.N8nActionRequest;
import com.workflow.dto.request.N8nCallbackRequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.N8nExecutionResult;
import com.workflow.entity.N8nExecutionRecord;
import com.workflow.repository.N8nExecutionRecordRepository;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * N8N 端到端集成测试
 * 测试组件间的完整交互流程，验证 Service Task 触发→回调→流程继续
 * 以及 N8N Action 同步执行的完整流程。
 *
 * 需求: 4.1, 4.6, 5.4, 5.5, 10.14, 10.16
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("N8N 端到端集成测试")
class N8nEndToEndIntegrationTest {

    // External dependency mocks
    @Mock(lenient = true) private AdminCenterClient adminCenterClient;
    @Mock(lenient = true) private N8nExecutionRecordRepository executionRecordRepository;
    @Mock(lenient = true) private StringRedisTemplate stringRedisTemplate;
    @Mock(lenient = true) private RestTemplate restTemplate;
    @Mock(lenient = true) private RuntimeService runtimeService;
    @Mock(lenient = true) private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Components under test
    private N8nTaskExecutor taskExecutor;
    private N8nCallbackHandler callbackHandler;
    private N8nExecutionController executionController;
    private N8nTimeoutChecker timeoutChecker;

    private static final String CALLBACK_BASE_URL = "http://workflow-engine:8081";
    private static final String WEBHOOK_URL = "https://n8n.example.com/webhook/test-wf";
    private static final String CONFIG_ID = "config-001";
    private static final String API_KEY = "test-api-key-123";
    private static final String PROCESS_INSTANCE_ID = "proc-inst-001";
    private static final String TASK_ID = "exec-001";

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // Initialize all components with shared mocks
        taskExecutor = new N8nTaskExecutor(
                adminCenterClient, executionRecordRepository,
                stringRedisTemplate, restTemplate, objectMapper
        );
        ReflectionTestUtils.setField(taskExecutor, "callbackBaseUrl", CALLBACK_BASE_URL);

        callbackHandler = new N8nCallbackHandler(
                executionRecordRepository, stringRedisTemplate,
                runtimeService, objectMapper
        );

        executionController = new N8nExecutionController(
                executionRecordRepository, taskExecutor
        );

        timeoutChecker = new N8nTimeoutChecker(
                executionRecordRepository, runtimeService
        );
    }

    // ==================== Service Task 完整流程测试 ====================

    @Nested
    @DisplayName("Service Task 触发 → N8N 回调 → 流程继续")
    class ServiceTaskFullFlowTests {

        /**
         * 测试完整的 Service Task 成功流程：
         * 1. N8nTaskExecutor.executeSynchronous() 模拟 Service Task 触发（使用 Action 模式模拟触发逻辑）
         *    - 获取 N8N 配置
         *    - 创建 ExecutionRecord (PENDING)
         *    - POST Webhook 成功
         *    - 更新为 RUNNING
         * 2. 模拟 Redis 中存储 callbackToken
         * 3. N8nCallbackHandler 接收回调
         *    - 验证 token
         *    - 写入输出变量
         *    - 触发流程继续
         *    - 更新为 SUCCESS
         *
         * 需求: 4.1, 4.6, 5.4, 5.5
         */
        @Test
        @DisplayName("Service Task 触发成功 → 回调成功 → 流程继续完整流程")
        void serviceTaskTriggerThenCallbackSuccess() {
            // ========== Phase 1: Service Task 触发 N8N Webhook ==========
            // Simulate the record creation and webhook call that N8nTaskExecutor.execute() does

            // Mock AdminCenterClient returns config
            Map<String, Object> n8nConfig = Map.of("apiKey", API_KEY, "baseUrl", "https://n8n.example.com");
            when(adminCenterClient.getN8nConfig(CONFIG_ID)).thenReturn(n8nConfig);

            // Track the saved records to simulate DB persistence
            List<N8nExecutionRecord> savedRecords = new ArrayList<>();
            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> {
                        N8nExecutionRecord record = invocation.getArgument(0);
                        if (record.getId() == null) {
                            record.setId(100L);
                        }
                        savedRecords.add(record);
                        return record;
                    });

            // Generate a callbackToken (simulating what execute() does)
            String callbackToken = taskExecutor.generateCallbackToken();
            assertThat(callbackToken).isNotNull().isNotBlank();

            // Build webhook request body (simulating what execute() does)
            Map<String, Object> inputData = Map.of("orderId", "ORD-001", "amount", 1500);
            String callbackUrl = taskExecutor.buildCallbackUrl();
            Map<String, Object> webhookBody = taskExecutor.buildWebhookRequestBody(inputData, callbackUrl, callbackToken);

            // Verify webhook body contains callback info
            assertThat(webhookBody).containsKey("callbackUrl");
            assertThat(webhookBody).containsKey("callbackToken");
            assertThat(webhookBody.get("callbackUrl")).isEqualTo(CALLBACK_BASE_URL + "/api/workflow/n8n/callback");
            assertThat(webhookBody.get("callbackToken")).isEqualTo(callbackToken);

            // Create ExecutionRecord (PENDING → RUNNING)
            N8nExecutionRecord record = new N8nExecutionRecord();
            record.setProcessInstanceId(PROCESS_INSTANCE_ID);
            record.setTaskId(TASK_ID);
            record.setN8nConfigId(CONFIG_ID);
            record.setWebhookUrl(WEBHOOK_URL);
            record.setCallbackToken(callbackToken);
            record.setStatus("PENDING");
            record.setSourceType("SERVICE_TASK");
            record.setInputData("{\"orderId\":\"ORD-001\",\"amount\":1500}");
            record.setStartedAt(Instant.now());
            record.setTimeoutSeconds(300);
            record = executionRecordRepository.save(record);

            // Simulate Redis storage of callbackToken
            String redisKey = "n8n:callback:" + callbackToken;
            when(valueOperations.get(redisKey)).thenReturn(String.valueOf(record.getId()));

            // Update to RUNNING (simulating successful webhook call)
            record.setStatus("RUNNING");
            executionRecordRepository.save(record);

            // ========== Phase 2: N8N Callback ==========
            // Simulate N8N calling back with success result
            when(executionRecordRepository.findById(100L)).thenReturn(Optional.of(record));
            when(runtimeService.getVariable(TASK_ID, "n8n_outputMapping"))
                    .thenReturn("[{\"source\":\"approvalStatus\",\"target\":\"orderApproved\"}]");

            N8nCallbackRequest callbackRequest = new N8nCallbackRequest();
            callbackRequest.setCallbackToken(callbackToken);
            callbackRequest.setStatus("success");
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("approvalStatus", "approved");
            outputData.put("processedBy", "auto-system");
            callbackRequest.setOutputData(outputData);

            ResponseEntity<ApiResponse<Void>> callbackResponse = callbackHandler.handleCallback(callbackRequest);

            // ========== Phase 3: Verify Complete Flow ==========
            // Callback should return 200 OK
            assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(callbackResponse.getBody()).isNotNull();
            assertThat(callbackResponse.getBody().isSuccess()).isTrue();

            // Verify output mapping was applied to process variables
            ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(runtimeService).setVariables(eq(TASK_ID), varsCaptor.capture());
            Map<String, Object> mappedVars = varsCaptor.getValue();
            assertThat(mappedVars).containsEntry("orderApproved", "approved");

            // Verify process continuation was triggered
            verify(runtimeService).trigger(TASK_ID);

            // Verify execution record was updated to SUCCESS
            ArgumentCaptor<N8nExecutionRecord> recordCaptor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository, atLeast(1)).save(recordCaptor.capture());
            N8nExecutionRecord finalRecord = recordCaptor.getAllValues().stream()
                    .filter(r -> "SUCCESS".equals(r.getStatus()))
                    .findFirst().orElse(null);
            assertThat(finalRecord).isNotNull();
            assertThat(finalRecord.getCompletedAt()).isNotNull();
            assertThat(finalRecord.getOutputData()).isNotNull();

            // Verify Redis key was cleaned up
            verify(stringRedisTemplate).delete(redisKey);
        }

        /**
         * 测试 Service Task Webhook 调用失败 → 重试全部失败 → 标记 FAILED
         *
         * 需求: 4.6
         */
        @Test
        @DisplayName("Service Task Webhook 调用失败 → 重试全部失败 → FAILED")
        void serviceTaskWebhookFailureAllRetriesFailed() {
            // Mock config
            Map<String, Object> n8nConfig = Map.of("apiKey", API_KEY);
            when(adminCenterClient.getN8nConfig(CONFIG_ID)).thenReturn(n8nConfig);

            // Mock webhook always fails
            when(restTemplate.exchange(eq(WEBHOOK_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> {
                        N8nExecutionRecord r = invocation.getArgument(0);
                        if (r.getId() == null) r.setId(200L);
                        return r;
                    });

            // Build action request to test via executeSynchronous (simulates the webhook call logic)
            N8nActionRequest request = new N8nActionRequest();
            request.setN8nConfigId(CONFIG_ID);
            request.setWebhookUrl(WEBHOOK_URL);
            request.setN8nWorkflowId("wf-001");
            request.setTimeoutSeconds(120);
            request.setProcessInstanceId(PROCESS_INSTANCE_ID);
            request.setTaskId(TASK_ID);
            request.setInputData(Map.of("param1", "value1"));

            N8nExecutionResult result = taskExecutor.executeSynchronous(request);

            // Should be FAILED
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatus()).isEqualTo("FAILED");
            assertThat(result.getErrorMessage()).contains("Connection refused");

            // Verify record was saved with FAILED status
            ArgumentCaptor<N8nExecutionRecord> captor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository, atLeast(1)).save(captor.capture());
            N8nExecutionRecord failedRecord = captor.getAllValues().stream()
                    .filter(r -> "FAILED".equals(r.getStatus()))
                    .findFirst().orElse(null);
            assertThat(failedRecord).isNotNull();
            assertThat(failedRecord.getErrorMessage()).isNotNull();
            assertThat(failedRecord.getCompletedAt()).isNotNull();
        }

        /**
         * 测试回调 token 无效 → 401 Unauthorized
         *
         * 需求: 5.5
         */
        @Test
        @DisplayName("回调 token 无效 → 401 Unauthorized")
        void callbackWithInvalidToken() {
            String invalidToken = "invalid-token-xyz";
            when(valueOperations.get("n8n:callback:" + invalidToken)).thenReturn(null);

            N8nCallbackRequest request = new N8nCallbackRequest();
            request.setCallbackToken(invalidToken);
            request.setStatus("success");
            request.setOutputData(Map.of("result", "ok"));

            ResponseEntity<ApiResponse<Void>> response = callbackHandler.handleCallback(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("UNAUTHORIZED");

            // Verify no process continuation was triggered
            verify(runtimeService, never()).trigger(anyString());
            verify(runtimeService, never()).setVariables(anyString(), anyMap());
        }

        /**
         * 测试回调在超时之后到达 → 410 Gone
         *
         * 需求: 5.5
         */
        @Test
        @DisplayName("回调在超时之后到达 → 410 Gone")
        void callbackAfterTimeout() {
            String callbackToken = "timeout-token-001";
            String redisKey = "n8n:callback:" + callbackToken;

            // Token still in Redis (within buffer period)
            when(valueOperations.get(redisKey)).thenReturn("300");

            // But the record is already TIMEOUT
            N8nExecutionRecord timedOutRecord = new N8nExecutionRecord();
            timedOutRecord.setId(300L);
            timedOutRecord.setProcessInstanceId(PROCESS_INSTANCE_ID);
            timedOutRecord.setTaskId(TASK_ID);
            timedOutRecord.setCallbackToken(callbackToken);
            timedOutRecord.setStatus("TIMEOUT");
            timedOutRecord.setSourceType("SERVICE_TASK");
            timedOutRecord.setStartedAt(Instant.now().minusSeconds(600));
            timedOutRecord.setTimeoutSeconds(300);
            when(executionRecordRepository.findById(300L)).thenReturn(Optional.of(timedOutRecord));

            N8nCallbackRequest request = new N8nCallbackRequest();
            request.setCallbackToken(callbackToken);
            request.setStatus("success");
            request.setOutputData(Map.of("result", "late-result"));

            ResponseEntity<ApiResponse<Void>> response = callbackHandler.handleCallback(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("GONE");

            // Verify Redis key was cleaned up
            verify(stringRedisTemplate).delete(redisKey);

            // Verify no process continuation was triggered
            verify(runtimeService, never()).trigger(anyString());
        }
    }

    // ==================== N8N Action 同步执行完整流程测试 ====================

    @Nested
    @DisplayName("N8N Action 执行 → 同步等待 → 返回结果")
    class ActionSyncExecutionFlowTests {

        /**
         * 测试完整的 N8N Action 同步执行成功流程：
         * 1. POST /api/v1/n8n/execute 接收请求
         * 2. N8nTaskExecutor.executeSynchronous() 调用 N8N Webhook
         * 3. 等待响应并返回结果
         * 4. 创建 ExecutionRecord (sourceType=ACTION, status=SUCCESS)
         *
         * 需求: 10.14, 10.16
         */
        @Test
        @DisplayName("Action 同步执行成功 → 返回结果完整流程")
        void actionSyncExecutionSuccess() {
            // Mock config
            Map<String, Object> n8nConfig = Map.of("apiKey", API_KEY);
            when(adminCenterClient.getN8nConfig(CONFIG_ID)).thenReturn(n8nConfig);

            // Mock webhook success response
            Map<String, Object> n8nResponse = new HashMap<>();
            n8nResponse.put("documentUrl", "https://storage.example.com/doc-001.pdf");
            n8nResponse.put("generatedAt", "2024-01-15T10:30:00Z");
            n8nResponse.put("pageCount", 5);
            ResponseEntity<Map> webhookResponse = new ResponseEntity<>(n8nResponse, HttpStatus.OK);
            when(restTemplate.exchange(eq(WEBHOOK_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(webhookResponse);

            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> {
                        N8nExecutionRecord r = invocation.getArgument(0);
                        if (r.getId() == null) r.setId(400L);
                        return r;
                    });

            // Build request through the controller
            N8nActionRequest request = new N8nActionRequest();
            request.setN8nConfigId(CONFIG_ID);
            request.setWebhookUrl(WEBHOOK_URL);
            request.setN8nWorkflowId("wf-doc-gen");
            request.setTimeoutSeconds(120);
            request.setProcessInstanceId(PROCESS_INSTANCE_ID);
            request.setTaskId(TASK_ID);
            request.setInputData(Map.of("templateId", "invoice-template", "orderId", "ORD-001"));

            // Execute through the controller (end-to-end)
            ResponseEntity<ApiResponse<N8nExecutionResult>> response =
                    executionController.executeSynchronous(request);

            // Verify response
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();

            N8nExecutionResult result = response.getBody().getData();
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            assertThat(result.getExecutionRecordId()).isEqualTo(400L);
            assertThat(result.getOutputData()).containsEntry("documentUrl", "https://storage.example.com/doc-001.pdf");
            assertThat(result.getOutputData()).containsEntry("pageCount", 5);

            // Verify execution record was saved (created then updated)
            // Note: Mockito captures references to the same mutable object, so both captures
            // point to the final state. We verify the record was saved at least twice
            // (once for creation, once for success update) and check the final state.
            verify(executionRecordRepository, times(2)).save(any(N8nExecutionRecord.class));

            ArgumentCaptor<N8nExecutionRecord> captor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository, atLeast(1)).save(captor.capture());

            // The final state of the record should be SUCCESS with ACTION source type
            N8nExecutionRecord finalRecord = captor.getValue(); // last captured value
            assertThat(finalRecord.getSourceType()).isEqualTo("ACTION");
            assertThat(finalRecord.getStatus()).isEqualTo("SUCCESS");
            assertThat(finalRecord.getProcessInstanceId()).isEqualTo(PROCESS_INSTANCE_ID);
            assertThat(finalRecord.getTaskId()).isEqualTo(TASK_ID);
            assertThat(finalRecord.getCompletedAt()).isNotNull();
            assertThat(finalRecord.getOutputData()).isNotNull();
        }

        /**
         * 测试 N8N Action 同步执行 Webhook 失败 → 返回错误
         *
         * 需求: 10.16
         */
        @Test
        @DisplayName("Action 同步执行失败 → 返回错误信息")
        void actionSyncExecutionFailure() {
            // Mock config
            Map<String, Object> n8nConfig = Map.of("apiKey", API_KEY);
            when(adminCenterClient.getN8nConfig(CONFIG_ID)).thenReturn(n8nConfig);

            // Mock webhook failure
            when(restTemplate.exchange(eq(WEBHOOK_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RestClientException("N8N service unavailable"));

            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> {
                        N8nExecutionRecord r = invocation.getArgument(0);
                        if (r.getId() == null) r.setId(500L);
                        return r;
                    });

            N8nActionRequest request = new N8nActionRequest();
            request.setN8nConfigId(CONFIG_ID);
            request.setWebhookUrl(WEBHOOK_URL);
            request.setN8nWorkflowId("wf-001");
            request.setTimeoutSeconds(120);
            request.setProcessInstanceId(PROCESS_INSTANCE_ID);
            request.setTaskId(TASK_ID);
            request.setInputData(Map.of("key", "value"));

            ResponseEntity<ApiResponse<N8nExecutionResult>> response =
                    executionController.executeSynchronous(request);

            // Controller returns 200 with failure result
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();

            N8nExecutionResult result = response.getBody().getData();
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatus()).isEqualTo("FAILED");
            assertThat(result.getErrorMessage()).contains("N8N service unavailable");

            // Verify record saved with FAILED status
            ArgumentCaptor<N8nExecutionRecord> captor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository, atLeast(1)).save(captor.capture());
            N8nExecutionRecord failedRecord = captor.getAllValues().stream()
                    .filter(r -> "FAILED".equals(r.getStatus()))
                    .findFirst().orElse(null);
            assertThat(failedRecord).isNotNull();
            assertThat(failedRecord.getSourceType()).isEqualTo("ACTION");
        }

        /**
         * 测试 N8N Action 请求参数校验 → 缺少 webhookUrl 返回 400
         */
        @Test
        @DisplayName("Action 请求缺少 webhookUrl → 400 Bad Request")
        void actionMissingWebhookUrlReturnsBadRequest() {
            N8nActionRequest request = new N8nActionRequest();
            request.setN8nConfigId(CONFIG_ID);
            request.setWebhookUrl(null); // missing
            request.setProcessInstanceId(PROCESS_INSTANCE_ID);

            ResponseEntity<ApiResponse<N8nExecutionResult>> response =
                    executionController.executeSynchronous(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        }

        /**
         * 测试 N8N Action 带输入/输出映射的完整流程
         *
         * 需求: 10.14
         */
        @Test
        @DisplayName("Action 带输入/输出映射的完整执行流程")
        void actionWithInputOutputMappingFullFlow() {
            // Mock config
            Map<String, Object> n8nConfig = Map.of("apiKey", API_KEY);
            when(adminCenterClient.getN8nConfig(CONFIG_ID)).thenReturn(n8nConfig);

            // Mock webhook response
            Map<String, Object> n8nResponse = new HashMap<>();
            n8nResponse.put("status", "completed");
            n8nResponse.put("totalAmount", 2500);
            n8nResponse.put("currency", "CNY");
            ResponseEntity<Map> webhookResponse = new ResponseEntity<>(n8nResponse, HttpStatus.OK);
            when(restTemplate.exchange(eq(WEBHOOK_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(webhookResponse);

            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> {
                        N8nExecutionRecord r = invocation.getArgument(0);
                        if (r.getId() == null) r.setId(600L);
                        return r;
                    });

            N8nActionRequest request = new N8nActionRequest();
            request.setN8nConfigId(CONFIG_ID);
            request.setWebhookUrl(WEBHOOK_URL);
            request.setN8nWorkflowId("wf-payment");
            request.setTimeoutSeconds(60);
            request.setProcessInstanceId(PROCESS_INSTANCE_ID);
            request.setTaskId(TASK_ID);
            request.setInputData(Map.of("userId", "user-001", "orderAmount", 2500));
            request.setInputMapping("[{\"source\":\"userId\",\"target\":\"customerId\"},{\"source\":\"orderAmount\",\"target\":\"amount\"}]");
            request.setOutputMapping("[{\"source\":\"status\",\"target\":\"paymentStatus\"},{\"source\":\"totalAmount\",\"target\":\"paidAmount\"}]");

            ResponseEntity<ApiResponse<N8nExecutionResult>> response =
                    executionController.executeSynchronous(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();

            N8nExecutionResult result = response.getBody().getData();
            assertThat(result.isSuccess()).isTrue();
            // Output mapping should map "status" → "paymentStatus" and "totalAmount" → "paidAmount"
            assertThat(result.getOutputData()).containsEntry("paymentStatus", "completed");
            assertThat(result.getOutputData()).containsEntry("paidAmount", 2500);
            // Unmapped fields should not be present
            assertThat(result.getOutputData()).doesNotContainKey("currency");
        }
    }

    // ==================== 超时检测集成测试 ====================

    @Nested
    @DisplayName("超时检测 → 标记 TIMEOUT → 后续回调被拒绝")
    class TimeoutDetectionFlowTests {

        /**
         * 测试超时检测器标记过期记录为 TIMEOUT，
         * 随后到达的回调被拒绝返回 410 Gone
         *
         * 需求: 5.5
         */
        @Test
        @DisplayName("超时检测标记 TIMEOUT → 后续回调返回 410")
        void timeoutDetectionThenCallbackRejected() {
            // ========== Phase 1: Setup a RUNNING record that has timed out ==========
            String callbackToken = "timeout-flow-token";
            N8nExecutionRecord record = new N8nExecutionRecord();
            record.setId(700L);
            record.setProcessInstanceId(PROCESS_INSTANCE_ID);
            record.setTaskId(TASK_ID);
            record.setCallbackToken(callbackToken);
            record.setStatus("RUNNING");
            record.setSourceType("SERVICE_TASK");
            record.setStartedAt(Instant.now().minusSeconds(600)); // Started 10 minutes ago
            record.setTimeoutSeconds(300); // 5 minute timeout → already expired

            // ========== Phase 2: Timeout checker detects and marks TIMEOUT ==========
            Instant now = Instant.now();
            boolean isTimedOut = timeoutChecker.isTimedOut(record, now);
            assertThat(isTimedOut).isTrue();

            // Simulate the timeout checker marking the record
            when(executionRecordRepository.findByStatusAndStartedAtBefore(eq("RUNNING"), any(Instant.class)))
                    .thenReturn(List.of(record));
            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            timeoutChecker.checkTimeouts();

            // Verify record was marked as TIMEOUT
            ArgumentCaptor<N8nExecutionRecord> captor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository, atLeast(1)).save(captor.capture());
            N8nExecutionRecord timeoutRecord = captor.getAllValues().stream()
                    .filter(r -> "TIMEOUT".equals(r.getStatus()))
                    .findFirst().orElse(null);
            assertThat(timeoutRecord).isNotNull();
            assertThat(timeoutRecord.getErrorMessage()).contains("timed out");
            assertThat(timeoutRecord.getCompletedAt()).isNotNull();

            // Verify Flowable error handling was triggered
            verify(runtimeService).trigger(TASK_ID);

            // ========== Phase 3: Late callback arrives → 410 Gone ==========
            // Reset mocks for callback phase
            reset(runtimeService);
            String redisKey = "n8n:callback:" + callbackToken;
            when(valueOperations.get(redisKey)).thenReturn("700");
            // Record is now TIMEOUT
            record.setStatus("TIMEOUT");
            when(executionRecordRepository.findById(700L)).thenReturn(Optional.of(record));

            N8nCallbackRequest callbackRequest = new N8nCallbackRequest();
            callbackRequest.setCallbackToken(callbackToken);
            callbackRequest.setStatus("success");
            callbackRequest.setOutputData(Map.of("result", "late-data"));

            ResponseEntity<ApiResponse<Void>> callbackResponse = callbackHandler.handleCallback(callbackRequest);

            assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.GONE);

            // Verify no process continuation was triggered for the late callback
            verify(runtimeService, never()).trigger(anyString());
        }

        /**
         * 测试非超时记录不被标记
         */
        @Test
        @DisplayName("未超时的 RUNNING 记录不被标记为 TIMEOUT")
        void nonTimedOutRecordNotMarked() {
            N8nExecutionRecord record = new N8nExecutionRecord();
            record.setId(800L);
            record.setProcessInstanceId(PROCESS_INSTANCE_ID);
            record.setTaskId(TASK_ID);
            record.setStatus("RUNNING");
            record.setSourceType("SERVICE_TASK");
            record.setStartedAt(Instant.now().minusSeconds(60)); // Started 1 minute ago
            record.setTimeoutSeconds(300); // 5 minute timeout → not expired yet

            Instant now = Instant.now();
            boolean isTimedOut = timeoutChecker.isTimedOut(record, now);
            assertThat(isTimedOut).isFalse();

            // Simulate timeout checker with this non-expired record
            when(executionRecordRepository.findByStatusAndStartedAtBefore(eq("RUNNING"), any(Instant.class)))
                    .thenReturn(List.of(record));

            timeoutChecker.checkTimeouts();

            // Record should NOT be saved with TIMEOUT status
            verify(executionRecordRepository, never()).save(argThat(r ->
                    r instanceof N8nExecutionRecord && "TIMEOUT".equals(((N8nExecutionRecord) r).getStatus())));
        }
    }

    // ==================== 跨组件数据流一致性测试 ====================

    @Nested
    @DisplayName("跨组件数据流一致性")
    class CrossComponentDataFlowTests {

        /**
         * 测试 Service Task 触发 → 失败回调 → 错误处理的完整流程
         */
        @Test
        @DisplayName("Service Task 触发 → N8N 失败回调 → 错误处理")
        void serviceTaskTriggerThenFailureCallback() {
            String callbackToken = "failure-flow-token";
            String redisKey = "n8n:callback:" + callbackToken;

            // Setup RUNNING record
            N8nExecutionRecord record = new N8nExecutionRecord();
            record.setId(900L);
            record.setProcessInstanceId(PROCESS_INSTANCE_ID);
            record.setTaskId(TASK_ID);
            record.setCallbackToken(callbackToken);
            record.setStatus("RUNNING");
            record.setSourceType("SERVICE_TASK");
            record.setStartedAt(Instant.now().minusSeconds(30));
            record.setTimeoutSeconds(300);

            when(valueOperations.get(redisKey)).thenReturn("900");
            when(executionRecordRepository.findById(900L)).thenReturn(Optional.of(record));
            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // N8N calls back with failure
            N8nCallbackRequest callbackRequest = new N8nCallbackRequest();
            callbackRequest.setCallbackToken(callbackToken);
            callbackRequest.setStatus("failed");
            callbackRequest.setErrorMessage("N8N workflow error: API rate limit exceeded");

            ResponseEntity<ApiResponse<Void>> response = callbackHandler.handleCallback(callbackRequest);

            // Verify response
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Verify record updated to FAILED
            ArgumentCaptor<N8nExecutionRecord> captor = ArgumentCaptor.forClass(N8nExecutionRecord.class);
            verify(executionRecordRepository, atLeast(1)).save(captor.capture());
            N8nExecutionRecord failedRecord = captor.getAllValues().stream()
                    .filter(r -> "FAILED".equals(r.getStatus()))
                    .findFirst().orElse(null);
            assertThat(failedRecord).isNotNull();
            assertThat(failedRecord.getErrorMessage()).contains("API rate limit exceeded");
            assertThat(failedRecord.getCompletedAt()).isNotNull();

            // Verify error handling was triggered
            verify(runtimeService).trigger(TASK_ID);

            // Verify Redis cleanup
            verify(stringRedisTemplate).delete(redisKey);
        }

        /**
         * 测试 N8N Action 配置不存在 → 返回失败
         */
        @Test
        @DisplayName("Action 执行时 N8N 配置不存在 → 返回失败")
        void actionConfigNotFoundReturnsFailure() {
            when(adminCenterClient.getN8nConfig("nonexistent-config")).thenReturn(null);

            when(executionRecordRepository.save(any(N8nExecutionRecord.class)))
                    .thenAnswer(invocation -> {
                        N8nExecutionRecord r = invocation.getArgument(0);
                        if (r.getId() == null) r.setId(1000L);
                        return r;
                    });

            N8nActionRequest request = new N8nActionRequest();
            request.setN8nConfigId("nonexistent-config");
            request.setWebhookUrl(WEBHOOK_URL);
            request.setN8nWorkflowId("wf-001");
            request.setTimeoutSeconds(120);
            request.setProcessInstanceId(PROCESS_INSTANCE_ID);
            request.setTaskId(TASK_ID);
            request.setInputData(Map.of("key", "value"));

            ResponseEntity<ApiResponse<N8nExecutionResult>> response =
                    executionController.executeSynchronous(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();

            N8nExecutionResult result = response.getBody().getData();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Failed to retrieve N8N config");
        }
    }
}
