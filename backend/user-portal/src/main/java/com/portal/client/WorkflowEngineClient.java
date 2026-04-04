package com.portal.client;

import com.platform.common.util.ApiResponseBodyUnwrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

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

    @Value("${workflow-engine.url:http://localhost:8081}")
    private String workflowEngineUrl;

    /** 与 application.yml 默认一致，避免未合并完整配置时静默关闭引擎集成 */
    @Value("${workflow-engine.enabled:true}")
    private boolean workflowEngineEnabled;

    private static final long HEALTH_CHECK_CACHE_TTL_MS = 30_000;
    private volatile boolean cachedAvailable = false;
    private volatile long lastHealthCheckTime = 0;

    /**
     * 检查 workflow-engine-core 是否可用（带 30 秒缓存）
     */
    public boolean isAvailable() {
        if (!workflowEngineEnabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastHealthCheckTime < HEALTH_CHECK_CACHE_TTL_MS) {
            return cachedAvailable;
        }
        try {
            String healthUrl = workflowEngineUrl + "/actuator/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);
            cachedAvailable = response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Workflow engine not available: {}", e.getMessage());
            cachedAvailable = false;
        }
        lastHealthCheckTime = now;
        return cachedAvailable;
    }

    /**
     * workflow-engine 对 /api/v1/** 要求已认证 JWT（与门户共用 {@code JWT_SECRET}）。
     * 将当前 HTTP 请求的 {@code Authorization} 原样转发；无请求上下文时不加头（如定时任务可能 403）。
     */
    private void forwardInboundAuthorization(HttpHeaders headers) {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && !auth.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, auth);
            }
        }
    }

    private HttpEntity<Void> authorizedGetEntity() {
        HttpHeaders headers = new HttpHeaders();
        forwardInboundAuthorization(headers);
        return new HttpEntity<>(headers);
    }

    // ==================== 流程部署与启动 ====================

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
            forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, 
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
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
            forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Failed to start process in workflow engine (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("Failed to start process in workflow engine (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to start process in workflow engine: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 删除引擎侧运行中与历史流程实例（内部清理；purge 路径在引擎侧 permitAll，无需 JWT）
     */
    public boolean purgeProcessInstance(String processInstanceId) {
        if (!isAvailable() || processInstanceId == null || processInstanceId.isEmpty()) {
            return false;
        }
        try {
            String url = workflowEngineUrl + "/api/v1/processes/instances/" + processInstanceId + "/purge";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
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

    // ==================== 任务查询 ====================

    /**
     * 查询用户待办任务
     */
    public Optional<Map<String, Object>> getUserTasks(String userId, int page, int size) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl(workflowEngineUrl + "/api/v1/tasks")
                    .queryParam("userId", userId)
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .encode()
                    .build()
                    .toUriString();
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get user tasks from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 查询流程实例的任务
     */
    public Optional<Map<String, Object>> getProcessInstanceTasks(String processInstanceId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl(workflowEngineUrl + "/api/v1/tasks")
                    .queryParam("processInstanceId", processInstanceId)
                    .queryParam("page", 0)
                    .queryParam("size", 100)
                    .encode()
                    .build()
                    .toUriString();
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get process instance tasks from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 查询用户所有可见任务（包括虚拟组和部门角色任务）
     */
    public Optional<Map<String, Object>> getUserAllVisibleTasks(String userId, List<String> groupIds, 
                                                                 List<String> deptRoles, int page, int size) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            UriComponentsBuilder ub = UriComponentsBuilder.fromHttpUrl(workflowEngineUrl + "/api/v1/tasks")
                    .queryParam("userId", userId)
                    .queryParam("page", page)
                    .queryParam("size", size);
            if (groupIds != null) {
                for (String groupId : groupIds) {
                    ub.queryParam("groupIds", groupId);
                }
            }
            if (deptRoles != null) {
                for (String deptRole : deptRoles) {
                    ub.queryParam("deptRoles", deptRole);
                }
            }
            String url = ub.encode().build().toUriString();
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get user all visible tasks from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 获取任务详情
     */
    public Optional<Map<String, Object>> getTaskById(String taskId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get task by id from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 统计用户任务数量
     */
    public Optional<Map<String, Object>> countUserTasks() {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/count";
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to count user tasks from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    // ==================== 任务操作（完成、认领、委托、转办、回退） ====================

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
            forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
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
            forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
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
            forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to delegate task in workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 取消认领任务
     */
    public Optional<Map<String, Object>> unclaimTask(String taskId, String userId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId + "/unclaim";
            
            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to unclaim task in workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 转办任务
     */
    public Optional<Map<String, Object>> transferTask(String taskId, String fromUserId, 
                                                       String toUserId, String reason) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId + "/transfer";
            
            Map<String, Object> request = new HashMap<>();
            request.put("fromUserId", fromUserId);
            request.put("toUserId", toUserId);
            request.put("reason", reason);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to transfer task in workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 回退任务到指定的历史节点
     */
    public Optional<Map<String, Object>> returnTask(String taskId, String targetActivityId, 
                                                     String userId, String reason) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId + "/return";
            
            Map<String, Object> request = new HashMap<>();
            request.put("targetActivityId", targetActivityId);
            request.put("userId", userId);
            request.put("reason", reason);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to return task in workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 获取可回退的历史节点列表
     */
    public Optional<List<Map<String, Object>>> getReturnableActivities(String taskId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId + "/returnable-activities";
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> activities = ApiResponseBodyUnwrap.normalizeToListOfMaps(response.getBody());
                return Optional.of(activities);
            }
        } catch (Exception e) {
            log.warn("Failed to get returnable activities from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    // ==================== 流程状态与历史查询 ====================

    /**
     * 获取流程实例状态
     * 用于检查流程是否已完成以及获取最后一个活动节点
     */
    public Optional<Map<String, Object>> getProcessInstanceStatus(String processInstanceId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/processes/" + processInstanceId + "/status";
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
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
     * 获取流程实例的当前活动节点
     */
    public Optional<Map<String, Object>> getCurrentActivity(String processInstanceId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/monitoring/processes/" + processInstanceId + "/current-activity";
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
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
     * 获取流程历史
     */
    public Optional<Map<String, Object>> getProcessHistory(String processInstanceId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/history/processes/" + processInstanceId;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
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
     * 获取任务历史（通过流程实例ID）
     */
    public Optional<List<Map<String, Object>>> getTaskHistory(String processInstanceId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/history/tasks?processInstanceId=" + processInstanceId;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = ApiResponseBodyUnwrap.unwrapDataMap(response.getBody());
                Object taskInstances = data.get("taskInstances");
                if (taskInstances instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tasks = (List<Map<String, Object>>) taskInstances;
                    return Optional.of(tasks);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get task history from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 获取流程实例流转历史（通过流程实例ID，包含用户名称解析）
     */
    public Optional<List<Map<String, Object>>> getProcessInstanceHistory(String processInstanceId) {
        log.debug("WorkflowEngineClient.getProcessInstanceHistory called for: {}", processInstanceId);
        if (!isAvailable()) {
            log.warn("Workflow engine not available");
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/process/" + processInstanceId + "/history";
            log.debug("Calling workflow engine URL: {}", url);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            log.debug("Response status: {}", response.getStatusCode());
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = ApiResponseBodyUnwrap.normalizeToListOfMaps(response.getBody());
                log.debug("Extracted {} records from response", data.size());
                return Optional.of(data);
            }
        } catch (Exception e) {
            log.error("Failed to get process instance history from workflow engine: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }
    
    /**
     * 获取任务流转历史（通过任务ID，包含用户名称解析）
     */
    public Optional<List<Map<String, Object>>> getTaskHistoryByTaskId(String taskId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId + "/history";
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = ApiResponseBodyUnwrap.normalizeToListOfMaps(response.getBody());
                return Optional.of(data);
            }
        } catch (Exception e) {
            log.warn("Failed to get task history by taskId from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    // ==================== 用户权限查询 ====================

    /**
     * 获取用户的任务权限信息（虚拟组和部门角色）
     */
    public Optional<Map<String, Object>> getUserTaskPermissions(String userId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/user-permissions?userId=" + userId;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get user task permissions from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 检查用户是否有任务操作权限
     */
    @SuppressWarnings("unchecked")
    public Optional<Boolean> checkTaskPermission(String taskId, String userId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId + "/check-permission?userId=" + userId;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = ApiResponseBodyUnwrap.unwrapDataMap(response.getBody());
                Object hasPermission = data.get("hasPermission");
                if (hasPermission instanceof Boolean) {
                    return Optional.of((Boolean) hasPermission);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check task permission from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 获取用户已处理的任务列表
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getCompletedTasks(String userId, int page, int size, 
                                                           String keyword, String startTime, String endTime) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            StringBuilder urlBuilder = new StringBuilder(workflowEngineUrl)
                .append("/api/v1/history/completed-tasks?userId=").append(userId)
                .append("&page=").append(page)
                .append("&size=").append(size);
            
            if (keyword != null && !keyword.isEmpty()) {
                urlBuilder.append("&keyword=").append(keyword);
            }
            if (startTime != null && !startTime.isEmpty()) {
                urlBuilder.append("&startTime=").append(startTime);
            }
            if (endTime != null && !endTime.isEmpty()) {
                urlBuilder.append("&endTime=").append(endTime);
            }
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                urlBuilder.toString(), HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get completed tasks from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 获取用户流程统计数据
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getProcessStatistics(String userId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/history/process-statistics?userId=" + userId;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get process statistics from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 取消（终止）流程实例
     */
    public Optional<Map<String, Object>> cancelProcessInstance(String processInstanceId, String reason) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/processes/instances/" + processInstanceId;
            
            Map<String, Object> request = new HashMap<>();
            request.put("reason", reason != null ? reason : "User withdrawn");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
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
     * 执行 N8N Action（同步模式）
     * 通过 workflow-engine-core 的 POST /api/v1/n8n/execute 内部端点转发执行请求
     *
     * Validates: Requirements 10.19
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> executeN8nAction(Map<String, Object> request) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/n8n/execute";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to execute N8N action via workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }

}
