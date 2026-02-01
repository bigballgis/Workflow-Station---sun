package com.portal.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

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
     * 查询流程实例的任务
     */
    public Optional<Map<String, Object>> getProcessInstanceTasks(String processInstanceId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks?processInstanceId=" + processInstanceId + "&page=0&size=100";
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
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
            StringBuilder urlBuilder = new StringBuilder(workflowEngineUrl)
                .append("/api/v1/tasks?userId=").append(userId)
                .append("&page=").append(page)
                .append("&size=").append(size);
            
            if (groupIds != null && !groupIds.isEmpty()) {
                for (String groupId : groupIds) {
                    urlBuilder.append("&groupIds=").append(groupId);
                }
            }
            
            if (deptRoles != null && !deptRoles.isEmpty()) {
                for (String deptRole : deptRoles) {
                    urlBuilder.append("&deptRoles=").append(deptRole);
                }
            }
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                urlBuilder.toString(), HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
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
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to get task by id from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 统计用户任务数量
     */
    public Optional<Map<String, Object>> countUserTasks(String userId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/count?userId=" + userId;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to count user tasks from workflow engine: {}", e.getMessage());
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
            
            // Ensure variables is not null
            if (variables == null) {
                variables = new HashMap<>();
            }
            
            // Put action into variables (not at top level)
            if (action != null) {
                variables.put("action", action);
            }
            
            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            request.put("variables", variables);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "WorkflowEngineClient.java:285", "message", "Response received from workflow-engine", "data", java.util.Map.of("taskId", taskId, "statusCode", response.getStatusCode().toString(), "hasBody", response.getBody() != null), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion

            // 即使 HTTP 状态码不是 2xx，也返回响应体，让调用方处理错误信息
            if (response.getBody() != null) {
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                    fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "WorkflowEngineClient.java:286", "message", "Returning response body", "data", java.util.Map.of("taskId", taskId, "responseKeys", response.getBody().keySet().toString()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                    fw.close();
                } catch (Exception ex) {}
                // #endregion
                return Optional.of(response.getBody());
            } else {
                log.error("Failed to complete task {}: HTTP status {}, empty response body", 
                    taskId, response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "WorkflowEngineClient.java:291", "message", "HttpClientErrorException caught", "data", java.util.Map.of("taskId", taskId, "statusCode", e.getStatusCode().toString(), "responseBody", e.getResponseBodyAsString() != null ? e.getResponseBodyAsString().substring(0, Math.min(200, e.getResponseBodyAsString().length())) : "null"), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            // 尝试解析错误响应体
            try {
                String responseBody = e.getResponseBodyAsString();
                if (responseBody != null && !responseBody.isEmpty()) {
                    // 尝试解析 JSON 响应
                    ObjectMapper objectMapper = new ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> errorResponse = objectMapper.readValue(responseBody, Map.class);
                    log.error("HTTP error completing task {}: status={}, response={}", 
                        taskId, e.getStatusCode(), errorResponse);
                    return Optional.of(errorResponse);
                }
            } catch (Exception parseException) {
                log.error("HTTP error completing task {}: status={}, response={}", 
                    taskId, e.getStatusCode(), e.getResponseBodyAsString());
            }
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "WorkflowEngineClient.java:308", "message", "HttpServerErrorException caught", "data", java.util.Map.of("taskId", taskId, "statusCode", e.getStatusCode().toString(), "responseBody", e.getResponseBodyAsString() != null ? e.getResponseBodyAsString().substring(0, Math.min(200, e.getResponseBodyAsString().length())) : "null"), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            // 尝试解析错误响应体
            try {
                String responseBody = e.getResponseBodyAsString();
                if (responseBody != null && !responseBody.isEmpty()) {
                    // 尝试解析 JSON 响应
                    ObjectMapper objectMapper = new ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> errorResponse = objectMapper.readValue(responseBody, Map.class);
                    
                    // #region agent log
                    try {
                        java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                        fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "WorkflowEngineClient.java:319", "message", "Parsed error response", "data", java.util.Map.of("taskId", taskId, "errorResponseKeys", errorResponse.keySet().toString()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                        fw.close();
                    } catch (Exception ex) {}
                    // #endregion
                    
                    log.error("Server error completing task {}: status={}, response={}", 
                        taskId, e.getStatusCode(), errorResponse);
                    return Optional.of(errorResponse);
                }
            } catch (Exception parseException) {
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                    fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "WorkflowEngineClient.java:322", "message", "Failed to parse error response", "data", java.util.Map.of("taskId", taskId, "parseException", parseException.getClass().getName(), "parseExceptionMessage", parseException.getMessage()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                    fw.close();
                } catch (Exception ex) {}
                // #endregion
                log.error("Server error completing task {}: status={}, response={}", 
                    taskId, e.getStatusCode(), e.getResponseBodyAsString());
            }
        } catch (Exception e) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/.cursor/debug.log", true);
                fw.write(java.util.Map.of("sessionId", "debug-session", "runId", "run1", "hypothesisId", "A", "location", "WorkflowEngineClient.java:325", "message", "General exception caught", "data", java.util.Map.of("taskId", taskId, "exceptionType", e.getClass().getName(), "exceptionMessage", e.getMessage()), "timestamp", System.currentTimeMillis()).toString() + "\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
            log.error("Failed to complete task {} in workflow engine: {}", taskId, e.getMessage(), e);
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
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
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
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
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
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
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
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> activities = (List<Map<String, Object>>) response.getBody().get("data");
                return Optional.ofNullable(activities);
            }
        } catch (Exception e) {
            log.warn("Failed to get returnable activities from workflow engine: {}", e.getMessage());
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
                Map<String, Object> body = response.getBody();
                // 响应格式: { success: true, data: { taskInstances: [...] } }
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tasks = (List<Map<String, Object>>) data.get("taskInstances");
                    return Optional.ofNullable(tasks);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get task history from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * 获取用户的任务权限信息（虚拟组和部门角色）
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getUserTaskPermissions(String userId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/user-permissions?userId=" + userId;
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                // 从 ApiResponse 中提取 data
                if (body.containsKey("data")) {
                    return Optional.of((Map<String, Object>) body.get("data"));
                }
                return Optional.of(body);
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
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                // 从 ApiResponse 中提取 data
                Map<String, Object> data = body.containsKey("data") 
                    ? (Map<String, Object>) body.get("data") 
                    : body;
                
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
                urlBuilder.toString(), HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                // 从 ApiResponse 中提取 data
                if (body.containsKey("data")) {
                    return Optional.of((Map<String, Object>) body.get("data"));
                }
                return Optional.of(body);
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
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                // 从 ApiResponse 中提取 data
                if (body.containsKey("data")) {
                    return Optional.of((Map<String, Object>) body.get("data"));
                }
                return Optional.of(body);
            }
        } catch (Exception e) {
            log.warn("Failed to get process statistics from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
