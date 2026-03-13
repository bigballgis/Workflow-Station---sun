package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.client.AdminCenterClient;
import com.workflow.dto.request.N8nActionRequest;
import com.workflow.dto.response.N8nExecutionResult;
import com.workflow.entity.N8nExecutionRecord;
import com.workflow.repository.N8nExecutionRecordRepository;
import com.workflow.util.N8nVariableMappingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * N8N 任务执行器
 * 实现 Flowable JavaDelegate 接口，处理 Service Task 类型的 N8N 自动化任务。
 * 同时提供 executeSynchronous 方法用于 N8N Action 同步执行模式。
 */
@Slf4j
@Component("n8nTaskExecutor")
@RequiredArgsConstructor
public class N8nTaskExecutor implements JavaDelegate {

    private static final String REDIS_KEY_PREFIX = "n8n:callback:";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_TIMEOUT = "TIMEOUT";
    private static final String SOURCE_SERVICE_TASK = "SERVICE_TASK";
    private static final String SOURCE_ACTION = "ACTION";

    /** 基础重试延迟（毫秒） */
    private static final long BASE_RETRY_DELAY_MS = 1000L;

    private final AdminCenterClient adminCenterClient;
    private final N8nExecutionRecordRepository executionRecordRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${platform.workflow-engine.callback-base-url:http://localhost:8081}")
    private String callbackBaseUrl;

    // ==================== JavaDelegate: Service Task 异步模式 ====================

    @Override
    public void execute(DelegateExecution execution) {
        String executionId = execution.getId();
        String processInstanceId = execution.getProcessInstanceId();
        log.info("N8nTaskExecutor triggered: executionId={}, processInstanceId={}", executionId, processInstanceId);

        // 1. 从扩展属性读取 N8N 配置
        String configId = getExtensionProperty(execution, "n8n:configId");
        String webhookUrl = getExtensionProperty(execution, "n8n:webhookUrl");
        String workflowId = getExtensionProperty(execution, "n8n:workflowId");
        String inputMappingJson = getExtensionProperty(execution, "n8n:inputMapping");
        String outputMappingJson = getExtensionProperty(execution, "n8n:outputMapping");
        int timeoutSeconds = parseIntOrDefault(getExtensionProperty(execution, "n8n:timeoutSeconds"), 300);
        int retryCount = parseIntOrDefault(getExtensionProperty(execution, "n8n:retryCount"), 3);

        if (configId == null || configId.isBlank()) {
            throw new RuntimeException("N8N configId is required but not configured on service task");
        }
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new RuntimeException("N8N webhookUrl is required but not configured on service task");
        }

        // 2. 获取 N8N 连接配置（含解密 apiKey）
        Map<String, Object> n8nConfig = adminCenterClient.getN8nConfig(configId);
        if (n8nConfig == null) {
            throw new RuntimeException("Failed to retrieve N8N config for configId: " + configId);
        }
        String apiKey = (String) n8nConfig.get("apiKey");

        // 3. 生成唯一 callbackToken
        String callbackToken = generateCallbackToken();

        // 4. 根据 inputMapping 提取流程变量，构建请求体
        Map<String, Object> processVariables = execution.getVariables();
        Map<String, Object> inputData = N8nVariableMappingUtil.applyInputMapping(processVariables, inputMappingJson);
        String callbackUrl = buildCallbackUrl();

        Map<String, Object> webhookBody = new LinkedHashMap<>();
        webhookBody.put("inputData", inputData);
        webhookBody.put("callbackUrl", callbackUrl);
        webhookBody.put("callbackToken", callbackToken);

        String inputDataJson = toJson(inputData);

        // 5. 创建 ExecutionRecord (status=PENDING)
        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setProcessInstanceId(processInstanceId);
        record.setTaskId(executionId);
        record.setN8nConfigId(configId);
        record.setN8nWorkflowId(workflowId);
        record.setWebhookUrl(webhookUrl);
        record.setCallbackToken(callbackToken);
        record.setStatus(STATUS_PENDING);
        record.setSourceType(SOURCE_SERVICE_TASK);
        record.setInputData(inputDataJson);
        record.setRetryCount(0);
        record.setStartedAt(Instant.now());
        record.setTimeoutSeconds(timeoutSeconds);
        record = executionRecordRepository.save(record);

        // 6. 存储 callbackToken 到 Redis
        long redisTtl = timeoutSeconds + 60L;
        stringRedisTemplate.opsForValue().set(
                REDIS_KEY_PREFIX + callbackToken,
                String.valueOf(record.getId()),
                redisTtl,
                TimeUnit.SECONDS
        );

        // Store outputMapping in execution variable for callback handler to use
        if (outputMappingJson != null && !outputMappingJson.isBlank()) {
            execution.setVariable("n8n_outputMapping", outputMappingJson);
        }

        // 7. HTTP POST 调用 N8N Webhook URL（含重试）
        boolean callSuccess = invokeWebhookWithRetry(webhookUrl, apiKey, webhookBody, record, retryCount);

        if (callSuccess) {
            // 成功：更新状态为 RUNNING，Flowable 异步等待
            record.setStatus(STATUS_RUNNING);
            executionRecordRepository.save(record);
            log.info("N8N webhook invoked successfully, task set to async wait: callbackToken={}", callbackToken);
            // Note: The execution will be signaled to continue by N8nCallbackHandler
            // For Flowable async wait, we do NOT call execution.setVariable or trigger completion here
            // The process will remain at this service task until the callback handler triggers it
        }
        // If callSuccess is false, the invokeWebhookWithRetry method already handled FAILED status and threw exception
    }

    // ==================== Action 同步执行模式 ====================

    /**
     * 同步执行 N8N 工作流（用于 Action 模式）。
     * 直接 HTTP POST 调用 N8N Webhook 并等待响应，不使用回调机制。
     */
    @SuppressWarnings("unchecked")
    public N8nExecutionResult executeSynchronous(N8nActionRequest request) {
        log.info("N8N Action synchronous execution: webhookUrl={}, processInstanceId={}",
                request.getWebhookUrl(), request.getProcessInstanceId());

        String configId = request.getN8nConfigId();
        String webhookUrl = request.getWebhookUrl();
        int timeoutSeconds = request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 120;

        // 获取 N8N 连接配置
        Map<String, Object> n8nConfig = adminCenterClient.getN8nConfig(configId);
        if (n8nConfig == null) {
            return N8nExecutionResult.failure(null, STATUS_FAILED,
                    "Failed to retrieve N8N config for configId: " + configId);
        }
        String apiKey = (String) n8nConfig.get("apiKey");

        // 构建请求体（Action 模式不需要 callbackUrl/callbackToken）
        Map<String, Object> inputData = request.getInputData();
        if (inputData == null) {
            inputData = Collections.emptyMap();
        }

        // Apply input mapping if provided
        if (request.getInputMapping() != null && !request.getInputMapping().isBlank()) {
            inputData = N8nVariableMappingUtil.applyInputMapping(inputData, request.getInputMapping());
        }

        Map<String, Object> webhookBody = new LinkedHashMap<>();
        webhookBody.put("inputData", inputData);

        String inputDataJson = toJson(inputData);

        // 创建 ExecutionRecord (sourceType=ACTION)
        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setProcessInstanceId(request.getProcessInstanceId());
        record.setTaskId(request.getTaskId());
        record.setN8nConfigId(configId);
        record.setN8nWorkflowId(request.getN8nWorkflowId());
        record.setWebhookUrl(webhookUrl);
        record.setStatus(STATUS_PENDING);
        record.setSourceType(SOURCE_ACTION);
        record.setInputData(inputDataJson);
        record.setRetryCount(0);
        record.setStartedAt(Instant.now());
        record.setTimeoutSeconds(timeoutSeconds);
        record = executionRecordRepository.save(record);

        // 同步 HTTP POST 调用
        try {
            HttpHeaders headers = buildHeaders(apiKey);
            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(webhookBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    webhookUrl, HttpMethod.POST, httpEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                String outputDataJson = toJson(responseBody);

                // Apply output mapping if provided
                Map<String, Object> outputData = responseBody;
                if (request.getOutputMapping() != null && !request.getOutputMapping().isBlank() && responseBody != null) {
                    outputData = N8nVariableMappingUtil.applyOutputMapping(responseBody, request.getOutputMapping());
                }

                record.setStatus(STATUS_SUCCESS);
                record.setOutputData(outputDataJson);
                record.setCompletedAt(Instant.now());
                executionRecordRepository.save(record);

                log.info("N8N Action executed successfully: recordId={}", record.getId());
                return N8nExecutionResult.success(record.getId(), outputData);
            } else {
                String errorMsg = "N8N webhook returned HTTP " + response.getStatusCode();
                record.setStatus(STATUS_FAILED);
                record.setErrorMessage(errorMsg);
                record.setCompletedAt(Instant.now());
                executionRecordRepository.save(record);

                log.error("N8N Action execution failed: {}", errorMsg);
                return N8nExecutionResult.failure(record.getId(), STATUS_FAILED, errorMsg);
            }
        } catch (Exception e) {
            String errorMsg = "N8N Action execution error: " + e.getMessage();
            record.setStatus(STATUS_FAILED);
            record.setErrorMessage(errorMsg);
            record.setCompletedAt(Instant.now());
            executionRecordRepository.save(record);

            log.error("N8N Action execution error: recordId={}", record.getId(), e);
            return N8nExecutionResult.failure(record.getId(), STATUS_FAILED, errorMsg);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 生成唯一的回调令牌
     */
    public String generateCallbackToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * 构建回调 URL
     */
    public String buildCallbackUrl() {
        return callbackBaseUrl + "/api/workflow/n8n/callback";
    }

    /**
     * 构建 Webhook 请求体（用于测试可见性）
     */
    public Map<String, Object> buildWebhookRequestBody(Map<String, Object> inputData,
                                                        String callbackUrl,
                                                        String callbackToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputData", inputData);
        body.put("callbackUrl", callbackUrl);
        body.put("callbackToken", callbackToken);
        return body;
    }

    /**
     * 计算第 attempt 次重试的延迟时间（毫秒），使用指数退避策略。
     * delay = BASE_RETRY_DELAY_MS * 2^attempt
     */
    public long calculateRetryDelay(int attempt) {
        return BASE_RETRY_DELAY_MS * (1L << attempt);
    }

    /**
     * 调用 N8N Webhook，失败时进行指数退避重试。
     * 所有重试失败后标记为 FAILED 并抛出异常。
     */
    private boolean invokeWebhookWithRetry(String webhookUrl, String apiKey,
                                            Map<String, Object> webhookBody,
                                            N8nExecutionRecord record, int maxRetries) {
        HttpHeaders headers = buildHeaders(apiKey);
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(webhookBody, headers);

        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = calculateRetryDelay(attempt - 1);
                    log.info("N8N webhook retry attempt {}/{}, delay={}ms, callbackToken={}",
                            attempt, maxRetries, delay, record.getCallbackToken());
                    Thread.sleep(delay);
                }

                ResponseEntity<Map> response = restTemplate.exchange(
                        webhookUrl, HttpMethod.POST, httpEntity, Map.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    record.setRetryCount(attempt);
                    return true;
                } else {
                    lastException = new RuntimeException("N8N webhook returned HTTP " + response.getStatusCode());
                    log.warn("N8N webhook call failed with HTTP {}: attempt {}/{}",
                            response.getStatusCode(), attempt, maxRetries);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                lastException = ie;
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn("N8N webhook call exception: attempt {}/{}, error={}",
                        attempt, maxRetries, e.getMessage());
            }
        }

        // All retries failed
        String errorMsg = "All " + (maxRetries + 1) + " attempts to call N8N webhook failed"
                + (lastException != null ? ": " + lastException.getMessage() : "");
        record.setStatus(STATUS_FAILED);
        record.setErrorMessage(errorMsg);
        record.setRetryCount(maxRetries);
        record.setCompletedAt(Instant.now());
        executionRecordRepository.save(record);

        log.error("N8N webhook invocation failed after all retries: callbackToken={}", record.getCallbackToken());
        throw new RuntimeException(errorMsg, lastException);
    }

    /**
     * 构建 HTTP 请求头（含 N8N API Key 认证）
     */
    private HttpHeaders buildHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-N8N-API-KEY", apiKey);
        }
        return headers;
    }

    /**
     * 从 BPMN Service Task 的 custom:Properties 扩展属性中读取指定属性值。
     * 属性名使用 n8n: 前缀（如 n8n:configId）。
     */
    private String getExtensionProperty(DelegateExecution execution, String propertyName) {
        FlowElement flowElement = execution.getCurrentFlowElement();
        if (!(flowElement instanceof ServiceTask serviceTask)) {
            return null;
        }

        Map<String, List<ExtensionElement>> extensionElements = serviceTask.getExtensionElements();
        if (extensionElements == null) {
            return null;
        }

        List<ExtensionElement> propertiesElements = extensionElements.get("properties");
        if (propertiesElements == null || propertiesElements.isEmpty()) {
            return null;
        }

        for (ExtensionElement propertiesElement : propertiesElements) {
            List<ExtensionElement> propertyElements = propertiesElement.getChildElements().get("property");
            if (propertyElements == null) {
                continue;
            }
            for (ExtensionElement propertyElement : propertyElements) {
                String name = propertyElement.getAttributeValue(null, "name");
                if (propertyName.equals(name)) {
                    return propertyElement.getAttributeValue(null, "value");
                }
            }
        }
        return null;
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return null;
        }
    }
}
