package com.portal.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Workflow Engine Core 客户端
 * 用于调用 workflow-engine-core 模块的 API
 * 
 * 注意：当前 workflow-engine-core 的 API 尚未完全实现，
 * 此客户端提供了回退机制，在 workflow-engine-core 不可用时使用本地实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngineClient {

    private final RestTemplate restTemplate;

    @Value("${workflow-engine.url:http://localhost:8091}")
    private String workflowEngineUrl;

    @Value("${workflow-engine.enabled:false}")
    private boolean workflowEngineEnabled;

    /**
     * 检查 workflow-engine-core 是否可用
     */
    public boolean isAvailable() {
        if (!workflowEngineEnabled) {
            return false;
        }
        try {
            String healthUrl = workflowEngineUrl + "/actuator/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Workflow engine not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 部署流程定义
     */
    public Optional<Map<String, Object>> deployProcess(String processKey, String bpmnXml, String name) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/processes/definitions/deploy";
            
            Map<String, Object> request = new HashMap<>();
            request.put("key", processKey);
            request.put("name", name);
            request.put("bpmnXml", bpmnXml);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, 
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to deploy process to workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 启动流程实例
     */
    public Optional<Map<String, Object>> startProcess(String processDefinitionKey, String businessKey, 
                                                       String startUserId, Map<String, Object> variables) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/processes/instances";
            
            Map<String, Object> request = new HashMap<>();
            request.put("processDefinitionKey", processDefinitionKey);
            request.put("businessKey", businessKey);
            request.put("startUserId", startUserId);
            request.put("variables", variables);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to start process in workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 查询用户待办任务
     */
    public Optional<Map<String, Object>> getUserTasks(String userId, int page, int size) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks?userId=" + userId + "&page=" + page + "&size=" + size;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to get user tasks from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 完成任务
     */
    public Optional<Map<String, Object>> completeTask(String taskId, String userId, 
                                                       String action, Map<String, Object> variables) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId + "/complete";
            
            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            request.put("action", action);
            request.put("variables", variables);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to complete task in workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 认领任务
     */
    public Optional<Map<String, Object>> claimTask(String taskId, String userId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId + "/claim";
            
            Map<String, Object> request = new HashMap<>();
            request.put("claimedBy", userId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to claim task in workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 委托任务
     */
    public Optional<Map<String, Object>> delegateTask(String taskId, String delegatorId, 
                                                       String delegateId, String reason) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId + "/delegate";
            
            Map<String, Object> request = new HashMap<>();
            request.put("delegatedBy", delegatorId);
            request.put("delegatedTo", delegateId);
            request.put("reason", reason);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to delegate task in workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 获取流程历史
     */
    public Optional<Map<String, Object>> getProcessHistory(String processInstanceId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/history/processes/" + processInstanceId;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to get process history from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 获取任务历史
     */
    public Optional<List<Map<String, Object>>> getTaskHistory(String processInstanceId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/history/tasks?processInstanceId=" + processInstanceId;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) response.getBody().get("taskInstances");
                return Optional.ofNullable(tasks);
            }
        } catch (Exception e) {
            log.warn("Failed to get task history from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
