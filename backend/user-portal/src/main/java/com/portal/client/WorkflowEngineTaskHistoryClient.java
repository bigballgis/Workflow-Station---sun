package com.portal.client;

import com.platform.common.util.ApiResponseBodyUnwrap;
import com.platform.common.util.SafeUrlInput;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 任务历史 & 用户权限相关调用协作类：任务/流程流转历史、用户任务权限、完成任务/统计的权限检查。
 *
 * <p>底层 HTTP 调用、URL、headers、payload 构造逐字保留；探活/鉴权等公共能力委托回门面
 * {@link WorkflowEngineClient}（{@code @Lazy} 破除构造期循环依赖）。
 */
@Slf4j
@Component
public class WorkflowEngineTaskHistoryClient {

    private final WorkflowEngineClient engine;

    public WorkflowEngineTaskHistoryClient(@Lazy @Autowired WorkflowEngineClient engine) {
        this.engine = engine;
    }

    // ==================== Task history ====================

    /**
     * Returns task history by process instance ID
     */
    public Optional<List<Map<String, Object>>> getTaskHistory(String processInstanceId) {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/history/tasks?processInstanceId=" + SafeUrlInput.encodeQueryValue(processInstanceId);

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.GET, engine.authorizedGetEntity(),
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
     * Returns process instance flow history with resolved display names
     */
    public Optional<List<Map<String, Object>>> getProcessInstanceHistory(String processInstanceId) {
        log.debug("WorkflowEngineClient.getProcessInstanceHistory called for: {}", processInstanceId);
        if (!engine.isAvailable()) {
            log.warn("Workflow engine not available");
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/tasks/process/" + SafeUrlInput.requirePathToken(processInstanceId) + "/history";
            log.debug("Calling workflow engine URL: {}", url);

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.GET, engine.authorizedGetEntity(),
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
     * Returns task flow history by task ID with resolved display names
     */
    public Optional<List<Map<String, Object>>> getTaskHistoryByTaskId(String taskId) {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/tasks/" + SafeUrlInput.requirePathToken(taskId) + "/history";

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.GET, engine.authorizedGetEntity(),
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

    // ==================== User permissions ====================

    /**
     * Returns user task permissions (virtual groups only).
     *
     * <p>{@code includeRoles=false}: the only consumer is
     * {@code WorkspaceTaskFilterComponent#fetchUserVirtualGroups}, which reads {@code virtualGroupIds}
     * and nothing else. Asking for roles would cost a second serial admin-center round-trip inside the
     * engine on a path every To Do and dashboard load blocks on.</p>
     */
    public Optional<Map<String, Object>> getUserTaskPermissions(String userId) {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/tasks/user-permissions?includeRoles=false&userId="
                    + SafeUrlInput.encodeQueryValue(userId);

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.GET, engine.authorizedGetEntity(),
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
     * Checks whether user may perform task operation
     */
    @SuppressWarnings("unchecked")
    public Optional<Boolean> checkTaskPermission(String taskId, String userId) {
        if (!engine.isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = engine.engineUrl() + "/api/v1/tasks/" + SafeUrlInput.requirePathToken(taskId) + "/check-permission?userId=" + SafeUrlInput.encodeQueryValue(userId);

            ResponseEntity<Map<String, Object>> response = engine.restTemplate().exchange(
                url, HttpMethod.GET, engine.authorizedGetEntity(),
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
}
