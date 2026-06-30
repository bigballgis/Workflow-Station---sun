package com.portal.client;

import com.platform.common.i18n.I18nService;
import com.platform.common.util.ApiResponseBodyUnwrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 流程相关调用协作类：流程部署/启动/清理、流程状态&历史、取消、BPMN XML 获取。
 *
 * <p>底层 HTTP 调用、URL、headers、payload 构造逐字保留；探活/鉴权/错误解析等公共能力
 * 委托回门面 {@link WorkflowEngineClient}（{@code @Lazy} 破除构造期循环依赖）。
 */
@Slf4j
@Component
public class WorkflowEngineProcessClient {

    private final WorkflowEngineClient engine;
    private final I18nService i18nService;

    public WorkflowEngineProcessClient(@Lazy @Autowired WorkflowEngineClient engine,
                                       I18nService i18nService) {
        this.engine = engine;
        this.i18nService = i18nService;
    }

    // ==================== Process deploy and start ====================

    /**
     * Deploys process definition
     */
    public Optional<Map<String, Object>> deployProcess(String processKey, String bpmnXml, String name) {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/processes/definitions/deploy";

            Map<String, Object> request = new HashMap<>();
            request.put("key", processKey);
            request.put("name", name);
            request.put("bpmnXml", bpmnXml);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            engine.forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String body = e.getResponseBodyAsString();
            String msg = engine.extractMessage(body);
            log.warn("Failed to deploy process to workflow engine (HTTP {}): {}", e.getStatusCode(), body);
            throw new IllegalStateException(
                    i18nService.getMessage("portal.deploy_process_failed", e.getStatusCode(), msg));
        } catch (Exception e) {
            log.warn("Failed to deploy process to workflow engine: {}", e.getMessage(), e);
            throw new IllegalStateException(
                    i18nService.getMessage("portal.deploy_process_failed_generic", e.getMessage()), e);
        }
        return Optional.empty();
    }

    /**
     * Starts process instance.
     * Throws IllegalStateException with engine business message on failure; callers need not check empty.
     */
    public Map<String, Object> startProcess(String processDefinitionKey, String businessKey,
                                                       String startUserId, Map<String, Object> variables) {
        if (!engine.isAvailable()) {
            throw new IllegalStateException("Workflow engine unavailable, cannot start process: " + processDefinitionKey);
        }
        try {
            String url = engine.engineUrl() + "/api/v1/processes/instances";

            Map<String, Object> request = new HashMap<>();
            request.put("processDefinitionKey", processDefinitionKey);
            request.put("businessKey", businessKey);
            request.put("startUserId", startUserId);
            request.put("variables", variables);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            engine.forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return ApiResponseBodyUnwrap.unwrapDataMap(response.getBody());
            }
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            String msg = engine.extractMessage(body);
            log.error("Failed to start process in workflow engine (HTTP {}): {}", e.getStatusCode(), body);
            throw new IllegalStateException("Failed to start process [" + e.getStatusCode() + "]: " + msg);
        } catch (HttpServerErrorException e) {
            String body = e.getResponseBodyAsString();
            String msg = engine.extractMessage(body);
            log.error("Failed to start process in workflow engine (HTTP {}): {}", e.getStatusCode(), body);
            throw new IllegalStateException("Failed to start process [" + e.getStatusCode() + "]: " + msg);
        } catch (Exception e) {
            log.error("Failed to start process in workflow engine: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to start process: " + e.getMessage());
        }
        // unreachable
        throw new IllegalStateException("Unexpected empty response from workflow engine");
    }

    /**
     * Deletes runtime and historic process instances on engine (internal purge; permitAll, no JWT)
     */
    public boolean purgeProcessInstance(String processInstanceId) {
        if (!engine.isAvailable() || processInstanceId == null || processInstanceId.isEmpty()) {
            return false;
        }
        try {
            String url = engine.engineUrl() + "/api/v1/processes/instances/" + processInstanceId + "/purge";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of(), headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Failed to purge process instance {} in workflow engine: {}", processInstanceId, e.getMessage());
            return false;
        }
    }

    // ==================== Process status and history ====================

    /**
     * Returns process instance status
     * Checks whether process completed and returns last activity node
     */
    public Optional<Map<String, Object>> getProcessInstanceStatus(String processInstanceId) {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/processes/" + processInstanceId + "/status";

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.GET, engine.authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get process instance status from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Returns current activity node for process instance
     */
    public Optional<Map<String, Object>> getCurrentActivity(String processInstanceId) {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/monitoring/processes/" + processInstanceId + "/current-activity";

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.GET, engine.authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = ApiResponseBodyUnwrap.unwrapDataMap(response.getBody());
                return body.isEmpty() ? Optional.empty() : Optional.of(body);
            }
        } catch (Exception e) {
            log.warn("Failed to get current activity from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Returns process history
     */
    public Optional<Map<String, Object>> getProcessHistory(String processInstanceId) {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/history/processes/" + processInstanceId;

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.GET, engine.authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get process history from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Returns multi-instance sub-process status (aggregated by sub-table row)
     */
    public Optional<Map<String, Object>> getMultiInstanceStatus(String processInstanceId) {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/workflow/multi-instance/" + processInstanceId + "/status";
            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.GET, engine.authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get multi-instance status from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Returns process instance detail (includes variables when available).
     */
    public Optional<Map<String, Object>> getProcessInstance(String processInstanceId) {
        if (!engine.isAvailable() || processInstanceId == null || processInstanceId.isEmpty()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/processes/instances/" + processInstanceId;

            HttpHeaders headers = new HttpHeaders();
            engine.forwardInboundAuthorization(headers);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.GET, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("Process instance not found in workflow engine: {}", processInstanceId);
        } catch (Exception e) {
            log.warn("Failed to get process instance from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Cancels (terminates) process instance
     */
    public Optional<Map<String, Object>> cancelProcessInstance(String processInstanceId, String reason) {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/processes/instances/" + processInstanceId;

            Map<String, Object> request = new HashMap<>();
            request.put("reason", reason != null ? reason : "User withdrawn");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            engine.forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.DELETE, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful()) {
                return Optional.ofNullable(response.getBody()).map(ApiResponseBodyUnwrap::unwrapDataMap);
            }
        } catch (Exception e) {
            log.warn("Failed to cancel process instance in workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Fetches BPMN XML by process definition key
     * @param processDefinitionKey process definition key
     * @return BPMN XML string, or Optional.empty() on failure
     */
    public Optional<String> getBpmnXml(String processDefinitionKey) {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/processes/definitions/" + processDefinitionKey + "/bpmn";
            HttpHeaders headers = new HttpHeaders();
            engine.forwardInboundAuthorization(headers);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                    url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object data = body.get("data");
                if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) data;
                    Object bpmnXml = dataMap.get("bpmnXml");
                    if (bpmnXml instanceof String) {
                        return Optional.of((String) bpmnXml);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get BPMN XML for processDefinitionKey={}: {}", processDefinitionKey, e.getMessage());
        }
        return Optional.empty();
    }
}
