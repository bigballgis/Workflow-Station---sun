package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.ServiceTaskActionRequest;
import com.workflow.dto.response.ServiceTaskExecutionResult;
import com.workflow.entity.ServiceTaskExecutionRecord;
import com.workflow.repository.ServiceTaskExecutionRecordRepository;
import com.workflow.util.ServiceTaskVariableMappingUtil;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
 * <p>That "must end with Return Response" is <b>enforced, not advisory</b>: AP publishes the sync
 * response only from that step, so a run that fails earlier returns {@code 204 No Content}. HTTP
 * 2xx alone therefore says nothing about whether the automation succeeded — see
 * {@link ApFlowNoResponseException}.
 *
 * <p><b>Who owns the wait:</b> AP does, via {@code AP_WEBHOOK_TIMEOUT_SECONDS} (300s here). The
 * shared {@code RestTemplate}'s 10-minute read timeout is deliberately longer so AP always answers
 * first — its 204 is a clean, attributable outcome, whereas a client-side abort leaves a flow still
 * running in AP with no way to tell whether its side effects happened. {@code ap:timeoutSeconds}
 * (BPMN, default 120) is therefore <b>recorded but not enforced</b>: wiring it to the HTTP client
 * would abort live flows at 60-120s and break every automation slower than that (the AI-generation
 * flow alone needs ~230s). Shortening the wait must be done on the AP side, not here.
 *
 * <p>That 300s is the <i>ceiling</i>, not the cost of a failure. A HERMES-PATCH in the vendored AP
 * (engine {@code flow.operation.ts} + worker {@code execute-flow.ts}) publishes the sync response
 * as soon as a run reaches a terminal state, so a flow that dies in 3s answers in ~3s instead of
 * burning the full timeout with the process instance wedged. The timeout survives only as the
 * backstop for a run that never terminates at all.
 *
 * <p>The AP instance is a single shared runtime per environment: the webhook base URL is
 * resolved from {@code activepieces.webhook-base-url}; the Service Task only stores the
 * {@code ap:flowId} (environment-portable, unlike a full URL). AP CE webhooks are
 * unauthenticated — the webhook URL itself is the secret — so no API key is sent.
 */
@Slf4j
@Component("apTaskExecutor")
@RequiredArgsConstructor
public class ServiceTaskExecutor implements JavaDelegate {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String SOURCE_SERVICE_TASK = "SERVICE_TASK";
    private static final String SOURCE_ACTION = "ACTION";

    /** Base retry delay (milliseconds) */
    private static final long BASE_RETRY_DELAY_MS = 1000L;

    private final ServiceTaskExecutionRecordRepository executionRecordRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    /** REQUIRES_NEW template for the failure record — see {@link #persistFailureOutsideCallerTx}. */
    private volatile TransactionTemplate failureTx;

    private TransactionTemplate failureTx() {
        TransactionTemplate local = failureTx;
        if (local == null) {
            synchronized (this) {
                local = failureTx;
                if (local == null) {
                    local = new TransactionTemplate(transactionManager);
                    local.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    failureTx = local;
                }
            }
        }
        return local;
    }

    /** Per-environment AP runtime base URL; the sync webhook path is appended to it. */
    @Value("${service-task.webhook-base-url:http://localhost:8086}")
    private String webhookBaseUrl;

    @Value("${file-service.base-url:http://localhost:8083}")
    private String fileServiceBaseUrl;

    // ==================== JavaDelegate: Service Task (synchronous) ====================

    @Override
    public void execute(DelegateExecution execution) {
        String executionId = execution.getId();
        String processInstanceId = execution.getProcessInstanceId();
        log.info("ServiceTaskExecutor triggered: executionId={}, processInstanceId={}", executionId, processInstanceId);

        // 1. Read AP config from BPMN extension attributes (ap: prefix)
        String flowId = getExtensionProperty(execution, "ap:flowId");
        String webhookUrlOverride = getExtensionProperty(execution, "ap:webhookUrl");
        String inputMappingJson = getExtensionProperty(execution, "ap:inputMapping");
        String outputMappingJson = getExtensionProperty(execution, "ap:outputMapping");
        // ap:timeoutSeconds is RECORDED, NOT ENFORCED — see the class javadoc. It is kept only so
        // the execution record shows what the designer asked for; do not wire it to the HTTP client.
        int timeoutSeconds = parseIntOrDefault(getExtensionProperty(execution, "ap:timeoutSeconds"), 120);
        int retryCount = parseIntOrDefault(getExtensionProperty(execution, "ap:retryCount"), 3);

        String webhookUrl = resolveWebhookUrl(flowId, webhookUrlOverride);
        validateWebhookUrl(webhookUrl);

        // 2. Build request body from process variables via input mapping
        Map<String, Object> processVariables = execution.getVariables();
        Map<String, Object> inputData = ServiceTaskVariableMappingUtil.applyInputMapping(processVariables, inputMappingJson);
        convertRelativeUrls(inputData);

        // 3. Create execution record (PENDING)
        ServiceTaskExecutionRecord record = newRecord(processInstanceId, executionId, flowId, webhookUrl,
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
            outputData = ServiceTaskVariableMappingUtil.applyOutputMapping(responseBody, outputMappingJson);
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
    public ServiceTaskExecutionResult executeSynchronous(ServiceTaskActionRequest request) {
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
            inputData = ServiceTaskVariableMappingUtil.applyInputMapping(inputData, request.getInputMapping());
        } else {
            inputData = new LinkedHashMap<>(inputData);
        }
        convertRelativeUrls(inputData);

        ServiceTaskExecutionRecord record = newRecord(request.getProcessInstanceId(), request.getTaskId(), flowId,
                webhookUrl, SOURCE_ACTION, toJson(inputData), timeoutSeconds);
        record = executionRecordRepository.save(record);

        try {
            Map<String, Object> responseBody = invokeWebhook(webhookUrl, inputData);

            Map<String, Object> outputData = responseBody;
            if (request.getOutputMapping() != null && !request.getOutputMapping().isBlank() && responseBody != null) {
                outputData = ServiceTaskVariableMappingUtil.applyOutputMapping(responseBody, request.getOutputMapping());
            }

            record.setStatus(STATUS_SUCCESS);
            record.setOutputData(toJson(responseBody));
            record.setCompletedAt(Instant.now());
            executionRecordRepository.save(record);

            log.info("AP Action executed successfully: recordId={}", record.getId());
            return ServiceTaskExecutionResult.success(record.getId(), outputData);
        } catch (Exception e) {
            String errorMsg = extractErrorMessage(e);
            record.setStatus(STATUS_FAILED);
            record.setErrorMessage(errorMsg);
            record.setCompletedAt(Instant.now());
            executionRecordRepository.save(record);
            log.error("AP Action execution failed: recordId={}, error={}", record.getId(), errorMsg);
            return ServiceTaskExecutionResult.failure(record.getId(), STATUS_FAILED, errorMsg);
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

    private ServiceTaskExecutionRecord newRecord(String processInstanceId, String taskId, String flowId,
                                        String webhookUrl, String sourceType, String inputDataJson,
                                        int timeoutSeconds) {
        ServiceTaskExecutionRecord record = new ServiceTaskExecutionRecord();
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
                                                       ServiceTaskExecutionRecord record, int maxRetries) {
        Exception lastException = null;
        int attemptsMade = 0;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            attemptsMade = attempt + 1;
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
            } catch (ApFlowNoResponseException nre) {
                // Deterministic, and every attempt blocks for the whole AP webhook timeout —
                // retrying only multiplies how long the process instance stays wedged.
                lastException = nre;
                log.warn("AP flow produced no response; not retrying: flowId={}", record.getApFlowId());
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn("AP webhook call exception: attempt {}/{}, error={}", attempt, maxRetries, e.getMessage());
            }
        }

        String errorMsg = "All " + attemptsMade + " attempt(s) to call AP webhook failed"
                + (lastException != null ? ": " + extractErrorMessage(lastException) : "");
        record.setStatus(STATUS_FAILED);
        record.setErrorMessage(errorMsg);
        record.setRetryCount(Math.max(0, attemptsMade - 1));
        record.setCompletedAt(Instant.now());
        persistFailureOutsideCallerTx(record);
        log.error("AP webhook invocation failed after {} attempt(s): flowId={}", attemptsMade, record.getApFlowId());
        throw new RuntimeException(errorMsg, lastException);
    }

    /**
     * Write the FAILED record in its own transaction, so it survives the rollback we are about to
     * cause by throwing.
     *
     * <p>This delegate runs inside Flowable's {@code complete()} transaction. Saving the record on
     * that connection means the row dies with the rollback — which is why {@code wf_ap_execution_record}
     * historically held nothing but SUCCESS rows: every failure erased its own evidence, exactly when
     * the evidence mattered. A fresh row (id left null) is inserted instead of re-saving the managed
     * instance, because the caller's PENDING insert is rolled back too — merging onto it would target
     * a row that never commits.
     *
     * <p>Best-effort by design: losing the audit row must not replace the real AP failure with a
     * persistence error, so failures here are logged, not thrown.
     */
    private void persistFailureOutsideCallerTx(ServiceTaskExecutionRecord inFlight) {
        try {
            ServiceTaskExecutionRecord row = new ServiceTaskExecutionRecord();
            row.setProcessInstanceId(inFlight.getProcessInstanceId());
            row.setTaskId(inFlight.getTaskId());
            row.setApFlowId(inFlight.getApFlowId());
            row.setWebhookUrl(inFlight.getWebhookUrl());
            row.setSourceType(inFlight.getSourceType());
            row.setInputData(inFlight.getInputData());
            row.setStatus(inFlight.getStatus());
            row.setErrorMessage(inFlight.getErrorMessage());
            row.setRetryCount(inFlight.getRetryCount());
            row.setStartedAt(inFlight.getStartedAt());
            row.setCompletedAt(inFlight.getCompletedAt());
            row.setTimeoutSeconds(inFlight.getTimeoutSeconds());
            failureTx().executeWithoutResult(status -> executionRecordRepository.save(row));
        } catch (Exception e) {
            log.warn("Failed to persist AP failure record for flowId={}: {}",
                    inFlight.getApFlowId(), e.getMessage());
        }
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
        // 204 is AP's "the run produced no flow response" signal, NOT a success with an empty
        // result: the sync webhook's response is published only by the "Return Response" piece
        // action, so a run that fails earlier (or a flow missing that step) leaves the listener
        // to expire and fall back to 204 + empty body. Treating it as success is what let a
        // failed automation advance the process with no imported data and no error anywhere.
        if (response.getStatusCode().equals(HttpStatus.NO_CONTENT)) {
            throw new ApFlowNoResponseException(webhookUrl);
        }
        Map<String, Object> body = response.getBody();
        return body != null ? body : Collections.emptyMap();
    }

    /**
     * The AP sync webhook returned 204 — the flow run never reached its "Return Response" step.
     *
     * <p>Deterministic by nature (a failing step fails the same way on the next call), and each
     * attempt costs the full AP webhook timeout, so callers must not retry it.
     */
    public static class ApFlowNoResponseException extends RuntimeException {
        public ApFlowNoResponseException(String webhookUrl) {
            super("AP flow returned no response (HTTP 204) from " + webhookUrl
                    + " — the run failed before its \"Return Response\" step, or the flow has no such step. "
                    + "Check the run history for this flow in Automation Studio.");
        }
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
