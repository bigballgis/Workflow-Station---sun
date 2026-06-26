package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.ApActionRequest;
import com.workflow.dto.response.ApExecutionResult;
import com.workflow.entity.ApExecutionRecord;
import com.workflow.repository.ApExecutionRecordRepository;
import com.workflow.util.ApVariableMappingUtil;
import com.platform.common.security.SsrfProtection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * Activepieces (AP) task executor.
 *
 * <p>Implements the Flowable {@link JavaDelegate} interface to handle BPMN Service Task
 * AP automation, and provides {@link #executeSynchronous} for the user-driven AP Action mode.
 *
 * <p>AP integration is <b>synchronous</b>: the engine POSTs the input payload to the AP
 * <i>sync</i> webhook ({@code /api/v1/webhooks/{flowId}/sync}); the flow must end with a
 * "Return Response" step so the result comes back in the HTTP response. The output is mapped
 * back into process variables inline — there is no callback token / async wait / Redis state.
 *
 * <p>The AP instance is a single shared runtime per environment: the webhook base URL is
 * resolved from {@code activepieces.webhook-base-url}; the Service Task only stores the
 * {@code ap:flowId} (environment-portable, unlike a full URL). AP CE webhooks are
 * unauthenticated — the webhook URL itself is the secret — so no API key is sent.
 */
@Slf4j
@Component("apTaskExecutor")
@RequiredArgsConstructor
public class ApTaskExecutor implements JavaDelegate {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String SOURCE_SERVICE_TASK = "SERVICE_TASK";
    private static final String SOURCE_ACTION = "ACTION";

    /** Base retry delay (milliseconds) */
    private static final long BASE_RETRY_DELAY_MS = 1000L;

    private final ApExecutionRecordRepository executionRecordRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** Per-environment AP runtime base URL; the sync webhook path is appended to it. */
    @Value("${activepieces.webhook-base-url:http://localhost:8086}")
    private String webhookBaseUrl;

    @Value("${file-service.base-url:http://localhost:8083}")
    private String fileServiceBaseUrl;

    // ==================== JavaDelegate: Service Task (synchronous) ====================

    @Override
    public void execute(DelegateExecution execution) {
        String executionId = execution.getId();
        String processInstanceId = execution.getProcessInstanceId();
        log.info("ApTaskExecutor triggered: executionId={}, processInstanceId={}", executionId, processInstanceId);

        // 1. Read AP config from BPMN extension attributes (ap: prefix)
        String flowId = getExtensionProperty(execution, "ap:flowId");
        String webhookUrlOverride = getExtensionProperty(execution, "ap:webhookUrl");
        String inputMappingJson = getExtensionProperty(execution, "ap:inputMapping");
        String outputMappingJson = getExtensionProperty(execution, "ap:outputMapping");
        int timeoutSeconds = parseIntOrDefault(getExtensionProperty(execution, "ap:timeoutSeconds"), 120);
        int retryCount = parseIntOrDefault(getExtensionProperty(execution, "ap:retryCount"), 3);

        String webhookUrl = resolveWebhookUrl(flowId, webhookUrlOverride);
        validateWebhookUrl(webhookUrl);

        // 2. Build request body from process variables via input mapping
        Map<String, Object> processVariables = execution.getVariables();
        Map<String, Object> inputData = ApVariableMappingUtil.applyInputMapping(processVariables, inputMappingJson);
        convertRelativeUrls(inputData);

        // 3. Create execution record (PENDING)
        ApExecutionRecord record = newRecord(processInstanceId, executionId, flowId, webhookUrl,
                SOURCE_SERVICE_TASK, toJson(inputData), timeoutSeconds);
        record = executionRecordRepository.save(record);

        // 4. Synchronous POST to the AP sync webhook (with retries)
        Map<String, Object> responseBody;
        try {
            responseBody = invokeWebhookWithRetry(webhookUrl, inputData, record, retryCount);
        } catch (RuntimeException e) {
            // record already marked FAILED inside invokeWebhookWithRetry; rethrow for BPMN error handling
            throw e;
        }

        // 5. Apply output mapping and write results back into process variables
        Map<String, Object> outputData = responseBody;
        if (outputMappingJson != null && !outputMappingJson.isBlank()) {
            outputData = ApVariableMappingUtil.applyOutputMapping(responseBody, outputMappingJson);
        }
        if (outputData != null && !outputData.isEmpty()) {
            execution.setVariables(outputData);
        }

        record.setStatus(STATUS_SUCCESS);
        record.setOutputData(toJson(responseBody));
        record.setCompletedAt(Instant.now());
        executionRecordRepository.save(record);
        log.info("AP service task completed: recordId={}, flowId={}", record.getId(), flowId);
        // Synchronous service task — returning here lets Flowable continue to the next node.
    }

    // ==================== Action synchronous execution ====================

    /**
     * Synchronously execute an AP flow for the user Action mode and return its output.
     */
    public ApExecutionResult executeSynchronous(ApActionRequest request) {
        String flowId = request.getApFlowId();
        String webhookUrl = resolveWebhookUrl(flowId, request.getWebhookUrl());
        validateWebhookUrl(webhookUrl);
        int timeoutSeconds = request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 120;

        log.info("AP Action synchronous execution: webhookUrl={}, processInstanceId={}",
                webhookUrl, request.getProcessInstanceId());

        // Build input data, apply input mapping if provided
        Map<String, Object> inputData = request.getInputData();
        if (inputData == null) {
            inputData = Collections.emptyMap();
        }
        if (request.getInputMapping() != null && !request.getInputMapping().isBlank()) {
            inputData = ApVariableMappingUtil.applyInputMapping(inputData, request.getInputMapping());
        } else {
            inputData = new LinkedHashMap<>(inputData);
        }
        convertRelativeUrls(inputData);

        ApExecutionRecord record = newRecord(request.getProcessInstanceId(), request.getTaskId(), flowId,
                webhookUrl, SOURCE_ACTION, toJson(inputData), timeoutSeconds);
        record = executionRecordRepository.save(record);

        try {
            Map<String, Object> responseBody = invokeWebhook(webhookUrl, inputData);

            Map<String, Object> outputData = responseBody;
            if (request.getOutputMapping() != null && !request.getOutputMapping().isBlank() && responseBody != null) {
                outputData = ApVariableMappingUtil.applyOutputMapping(responseBody, request.getOutputMapping());
            }

            record.setStatus(STATUS_SUCCESS);
            record.setOutputData(toJson(responseBody));
            record.setCompletedAt(Instant.now());
            executionRecordRepository.save(record);

            log.info("AP Action executed successfully: recordId={}", record.getId());
            return ApExecutionResult.success(record.getId(), outputData);
        } catch (Exception e) {
            String errorMsg = extractErrorMessage(e);
            record.setStatus(STATUS_FAILED);
            record.setErrorMessage(errorMsg);
            record.setCompletedAt(Instant.now());
            executionRecordRepository.save(record);
            log.error("AP Action execution failed: recordId={}, error={}", record.getId(), errorMsg);
            return ApExecutionResult.failure(record.getId(), STATUS_FAILED, errorMsg);
        }
    }

    // ==================== Internal Methods ====================

    /**
     * Resolve the AP sync webhook URL. Uses the explicit override when present, otherwise
     * builds {@code <base>/api/v1/webhooks/<flowId>/sync}.
     */
    public String resolveWebhookUrl(String flowId, String webhookUrlOverride) {
        if (webhookUrlOverride != null && !webhookUrlOverride.isBlank()) {
            return webhookUrlOverride.trim();
        }
        if (flowId == null || flowId.isBlank()) {
            throw new RuntimeException("AP flowId is required but not configured on the service task");
        }
        String base = webhookBaseUrl.endsWith("/")
                ? webhookBaseUrl.substring(0, webhookBaseUrl.length() - 1)
                : webhookBaseUrl;
        return base + "/api/v1/webhooks/" + flowId.trim() + "/sync";
    }

    /**
     * SSRF-validate the webhook URL. The configured AP host is allow-listed (it is a trusted
     * per-environment value and may be a private/Docker DNS name); any other private host is
     * still blocked.
     */
    private void validateWebhookUrl(String webhookUrl) {
        SsrfProtection.validate(webhookUrl, allowedHosts());
    }

    private Set<String> allowedHosts() {
        try {
            String host = new URI(webhookBaseUrl.trim()).getHost();
            if (host != null && !host.isBlank()) {
                return Set.of(host.toLowerCase());
            }
        } catch (Exception e) {
            log.warn("Could not parse AP webhook-base-url host: {}", e.getMessage());
        }
        return Set.of();
    }

    private ApExecutionRecord newRecord(String processInstanceId, String taskId, String flowId,
                                        String webhookUrl, String sourceType, String inputDataJson,
                                        int timeoutSeconds) {
        ApExecutionRecord record = new ApExecutionRecord();
        record.setProcessInstanceId(processInstanceId);
        record.setTaskId(taskId);
        record.setApFlowId(flowId);
        record.setWebhookUrl(webhookUrl);
        record.setStatus(STATUS_PENDING);
        record.setSourceType(sourceType);
        record.setInputData(inputDataJson);
        record.setRetryCount(0);
        record.setStartedAt(Instant.now());
        record.setTimeoutSeconds(timeoutSeconds);
        return record;
    }

    /**
     * POST to the AP sync webhook with exponential backoff. On terminal failure, marks the
     * record FAILED and throws a RuntimeException (so a BPMN error boundary can react).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeWebhookWithRetry(String webhookUrl, Map<String, Object> inputData,
                                                       ApExecutionRecord record, int maxRetries) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = calculateRetryDelay(attempt - 1);
                    log.info("AP webhook retry attempt {}/{}, delay={}ms, flowId={}",
                            attempt, maxRetries, delay, record.getApFlowId());
                    Thread.sleep(delay);
                }
                Map<String, Object> body = invokeWebhook(webhookUrl, inputData);
                record.setRetryCount(attempt);
                return body;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                lastException = ie;
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn("AP webhook call exception: attempt {}/{}, error={}", attempt, maxRetries, e.getMessage());
            }
        }

        String errorMsg = "All " + (maxRetries + 1) + " attempts to call AP webhook failed"
                + (lastException != null ? ": " + extractErrorMessage(lastException) : "");
        record.setStatus(STATUS_FAILED);
        record.setErrorMessage(errorMsg);
        record.setRetryCount(maxRetries);
        record.setCompletedAt(Instant.now());
        executionRecordRepository.save(record);
        log.error("AP webhook invocation failed after all retries: flowId={}", record.getApFlowId());
        throw new RuntimeException(errorMsg, lastException);
    }

    /**
     * Single synchronous POST to the AP sync webhook. Returns the parsed JSON response body.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeWebhook(String webhookUrl, Map<String, Object> inputData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(inputData, headers);

        ResponseEntity<Map> response = restTemplate.exchange(webhookUrl, HttpMethod.POST, httpEntity, Map.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("AP webhook returned HTTP " + response.getStatusCode());
        }
        Map<String, Object> body = response.getBody();
        return body != null ? body : Collections.emptyMap();
    }

    /**
     * Calculate retry delay for the given attempt (milliseconds), using exponential backoff.
     */
    public long calculateRetryDelay(int attempt) {
        return BASE_RETRY_DELAY_MS * (1L << attempt);
    }

    /**
     * Read the specified property value from the custom:properties extension attributes
     * of a BPMN Service Task. Property names use the ap: prefix (e.g. ap:flowId).
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

    /**
     * Extract a meaningful error message, preferring the AP HTTP error response body.
     */
    private String extractErrorMessage(Throwable e) {
        if (e instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
            String responseBodyStr = httpEx.getResponseBodyAsString();
            String msg = "AP webhook returned HTTP " + httpEx.getStatusCode();
            if (responseBodyStr != null && !responseBodyStr.isBlank()) {
                msg += ": " + responseBodyStr.substring(0, Math.min(responseBodyStr.length(), 500));
            }
            return msg;
        }
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    /**
     * Convert relative file paths (e.g. /api/v1/upload/files/xxx.pdf) to absolute URLs
     * so that AP can access them via the Docker network.
     */
    @SuppressWarnings("unchecked")
    private void convertRelativeUrls(Map<String, Object> data) {
        if (data == null) return;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str && str.startsWith("/api/")) {
                entry.setValue(fileServiceBaseUrl + str);
            } else if (value instanceof List<?> list) {
                List<Object> converted = new java.util.ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof String str && str.startsWith("/api/")) {
                        converted.add(fileServiceBaseUrl + str);
                    } else {
                        converted.add(item);
                    }
                }
                entry.setValue(converted);
            }
        }
    }
}
