package com.portal.client;

import com.platform.common.constant.PlatformConstants;
import com.platform.common.i18n.I18nService;
import com.platform.common.util.ApiResponseBodyUnwrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.security.config.JwtProperties;
import com.platform.security.util.SecurityContextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Workflow Engine Core client
 * Client for workflow-engine-core module APIs
 * 
 * Note: workflow-engine-core APIs are not fully implemented yet;
 * Provides fallback to local implementation when workflow-engine-core is unavailable
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngineClient {

    private final RestTemplate restTemplate;
    private final JwtProperties jwtProperties;
    private final I18nService i18nService;

    @Value("${workflow-engine.url:http://localhost:8081}")
    private String workflowEngineUrl;

    /** Matches application.yml default so missing merged config does not silently disable engine integration */
    @Value("${workflow-engine.enabled:true}")
    private boolean workflowEngineEnabled;

    private static final long HEALTH_CHECK_CACHE_TTL_MS = 30_000;
    private volatile boolean cachedAvailable = false;
    private volatile long lastHealthCheckTime = 0;

    /**
     * Checks whether workflow-engine-core is available (30s cache)
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
     * workflow-engine requires authenticated JWT for /api/v1/** (same {@code JWT_SECRET} as portal).
     *
     * <p>Resolution order matches {@link com.platform.security.filter.JwtAuthenticationFilter#extractToken}:
     * Prefer {@code Authorization} header; else fall back to {@code platform.security.jwt.cookie-names}
     * configured httpOnly cookies (user-portal writes {@code up_access_token}),
     * and sends {@code Authorization: Bearer <token>} to workflow-engine (cross-service calls do not forward cookies).
     *
     * <p>No request context (e.g. scheduled job): no header — protected APIs return 403; caller handles.
     */
    private void forwardInboundAuthorization(HttpHeaders headers) {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return;
        }
        HttpServletRequest request = servletAttrs.getRequest();
        String auth = request.getHeader(PlatformConstants.HEADER_AUTHORIZATION);
        if (auth != null && !auth.isBlank()) {
            headers.set(PlatformConstants.HEADER_AUTHORIZATION, auth.trim());
            return;
        }
        String cookieToken = extractAccessTokenFromCookie(request);
        if (cookieToken != null && !cookieToken.isBlank()) {
            headers.set(PlatformConstants.HEADER_AUTHORIZATION,
                    PlatformConstants.HEADER_BEARER_PREFIX + cookieToken.trim());
        }
    }

    private String extractAccessTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        List<String> names = jwtProperties.getCookieNames();
        if (names == null || names.isEmpty()) {
            names = List.of("access_token");
        }
        for (String name : names) {
            for (Cookie cookie : cookies) {
                if (cookie != null
                        && name.equals(cookie.getName())
                        && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /** Extracts business message field from engine JSON response body */
    private String extractMessage(String body) {
        if (body == null || body.isBlank()) return "Unknown error";
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(body);
            // Supports { "message": "..." }, { "data": { "message": "..." } }, or { "error": "..." }
            JsonNode msg = node.path("message");
            if (msg.isMissingNode()) msg = node.path("data").path("message");
            if (msg.isMissingNode()) msg = node.path("error");
            if (!msg.isMissingNode() && !msg.isNull()) return msg.asText();
        } catch (Exception ignored) { }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private HttpEntity<Void> authorizedGetEntity() {
        HttpHeaders headers = new HttpHeaders();
        forwardInboundAuthorization(headers);
        return new HttpEntity<>(headers);
    }

    // ==================== Process deploy and start ====================

    /**
     * Deploys process definition
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
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String body = e.getResponseBodyAsString();
            String msg = extractMessage(body);
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
        if (!isAvailable()) {
            throw new IllegalStateException("Workflow engine unavailable, cannot start process: " + processDefinitionKey);
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
                return ApiResponseBodyUnwrap.unwrapDataMap(response.getBody());
            }
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            String msg = extractMessage(body);
            log.error("Failed to start process in workflow engine (HTTP {}): {}", e.getStatusCode(), body);
            throw new IllegalStateException("Failed to start process [" + e.getStatusCode() + "]: " + msg);
        } catch (HttpServerErrorException e) {
            String body = e.getResponseBodyAsString();
            String msg = extractMessage(body);
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

    // ==================== Task queries ====================

    /**
     * Queries user todo tasks
     */
    public Optional<Map<String, Object>> getUserTasks(String userId, int page, int size) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            UriComponentsBuilder ub = UriComponentsBuilder.fromHttpUrl(workflowEngineUrl + "/api/v1/tasks")
                    .queryParam("userId", userId)
                    .queryParam("page", page)
                    .queryParam("size", size);
            SecurityContextUtils.getCurrentActiveBusinessUnitId()
                    .filter(id -> id != null && !id.isBlank())
                    .ifPresent(bu -> ub.queryParam("activeBusinessUnitId", bu));
            String url = ub.encode().build().toUriString();
            
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
     * Queries tasks for process instance
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
     * Queries all tasks visible to user (virtual groups and department roles)
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
            SecurityContextUtils.getCurrentActiveBusinessUnitId()
                    .filter(id -> id != null && !id.isBlank())
                    .ifPresent(bu -> ub.queryParam("activeBusinessUnitId", bu));
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
     * Returns task detail
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
     * Queries parent task sub-table data (backfill rowId / live sync before assign).
     */
    public Optional<Map<String, Object>> getSubTableDataAll(String taskId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/workflow/multi-instance/tasks/" + taskId + "/sub-table-data/all";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, authorizedGetEntity(),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("Failed to get sub-table-data/all from workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Counts user tasks
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

    // ==================== Task operations (complete, claim, delegate, transfer, return) ====================

    /**
     * Completes task
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
            return Optional.empty();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String msg = parseWorkflowEngineErrorMessage(e.getResponseBodyAsString());
            log.warn("Failed to complete task in workflow engine: status={}, message={}", e.getStatusCode(), msg);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", msg != null ? msg : e.getMessage());
            return Optional.of(err);
        } catch (Exception e) {
            log.warn("Failed to complete task in workflow engine: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Claims task
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
     * Delegates task
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
     * Unclaims task
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
     * Transfers task
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

    private static final ObjectMapper ENGINE_ERROR_JSON = new ObjectMapper();

    /**
     * Parses human-readable message from workflow-engine ApiResponse error body.
     * Supports top-level {@code message}, {@code error.message} / {@code error.detail}, {@code errorMessage} (sub-table assign DTO).
     */
    private static String parseWorkflowEngineErrorMessage(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = ENGINE_ERROR_JSON.readTree(json);
            if (root.hasNonNull("message")) {
                String m = root.get("message").asText("");
                if (!m.isBlank()) {
                    return m;
                }
            }
            JsonNode err = root.get("error");
            if (err != null && err.isObject()) {
                if (err.hasNonNull("message")) {
                    String m = err.get("message").asText("");
                    if (!m.isBlank()) {
                        return m;
                    }
                }
                if (err.hasNonNull("detail")) {
                    String m = err.get("detail").asText("");
                    if (!m.isBlank()) {
                        return m;
                    }
                }
                if (err.hasNonNull("code")) {
                    String c = err.get("code").asText("");
                    if (!c.isBlank()) {
                        return c;
                    }
                }
            }
            if (root.hasNonNull("errorMessage")) {
                String m = root.get("errorMessage").asText("");
                if (!m.isBlank()) {
                    return m;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * Assigns sub-table row assignee (MI sub-process prerequisite task)
     */
    public Optional<Map<String, Object>> assignSubTableRow(String taskId, long rowId, String assigneeId) {
        return assignSubTableRow(taskId, rowId, assigneeId, null);
    }

    /**
     * @param rowKey required for composite PK (path rowId may be placeholder; engine uses body)
     */
    public Optional<Map<String, Object>> assignSubTableRow(String taskId, long rowId, String assigneeId,
                                                           Map<String, Object> rowKey) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/tasks/" + taskId + "/sub-table-rows/" + rowId + "/assign";

            Map<String, Object> request = new HashMap<>();
            request.put("assigneeId", assigneeId);
            if (rowKey != null && !rowKey.isEmpty()) {
                request.put("rowKey", rowKey);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            forwardInboundAuthorization(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> unwrapped = ApiResponseBodyUnwrap.unwrapDataMap(response.getBody());
                return Optional.of(unwrapped);
            }
        } catch (HttpClientErrorException e) {
            String raw = e.getResponseBodyAsString();
            String msg = parseWorkflowEngineErrorMessage(raw);
            log.warn("assignSubTableRow client error: status={}, message={}", e.getStatusCode(), msg);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", msg != null ? msg : "Assignment failed");
            return Optional.of(err);
        } catch (HttpServerErrorException e) {
            String raw = e.getResponseBodyAsString();
            String msg = parseWorkflowEngineErrorMessage(raw);
            log.warn("assignSubTableRow server error: status={}, message={}", e.getStatusCode(), msg);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", msg != null ? msg : "Assignment failed");
            return Optional.of(err);
        } catch (Exception e) {
            log.warn("Failed to assign sub-table row in workflow engine: {}", e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Returns task to specified historic activity
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
     * Returns list of returnable historic activity nodes
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

    // ==================== Process status and history ====================

    /**
     * Returns process instance status
     * Checks whether process completed and returns last activity node
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
     * Returns current activity node for process instance
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
     * Returns process history
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
     * Returns multi-instance sub-process status (aggregated by sub-table row)
     */
    public Optional<Map<String, Object>> getMultiInstanceStatus(String processInstanceId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/workflow/multi-instance/" + processInstanceId + "/status";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, authorizedGetEntity(),
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
     * Returns task history by process instance ID
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
     * Returns process instance flow history with resolved display names
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
     * Returns task flow history by task ID with resolved display names
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
    
    // ==================== User permissions ====================

    /**
     * Returns user task permissions (virtual groups and department roles)
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
     * Checks whether user may perform task operation
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
     * Returns user's completed task list
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
     * Returns user process statistics
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
     * Cancels (terminates) process instance
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
     * Executes N8N action (synchronous)
     * Forwards execution via workflow-engine-core POST /api/v1/n8n/execute internal endpoint
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

    /**
     * Fetches BPMN XML by process definition key
     * @param processDefinitionKey process definition key
     * @return BPMN XML string, or Optional.empty() on failure
     */
    public Optional<String> getBpmnXml(String processDefinitionKey) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String url = workflowEngineUrl + "/api/v1/processes/definitions/" + processDefinitionKey + "/bpmn";
            HttpHeaders headers = new HttpHeaders();
            forwardInboundAuthorization(headers);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
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
