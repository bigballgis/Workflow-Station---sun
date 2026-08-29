package com.portal.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.platform.common.util.ApiResponseBodyUnwrap;
import com.platform.common.util.SafeUrlInput;

import java.util.*;

/**
 * Admin Center REST client.
 * Provides typed methods for calling admin-center APIs from user-portal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminCenterClient {

    private final RestTemplate restTemplate;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    /**
     * Check if admin-center is available.
     */
    public boolean isAvailable() {
        try {
            String url = adminCenterUrl + "/api/v1/admin/actuator/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Admin center not available: {}", e.getMessage());
            return false;
        }
    }

    // ==================== Permission Requests ====================

    public Optional<Map<String, Object>> createVirtualGroupRequest(String userId, String virtualGroupId, String reason) {
        return post("/api/v1/admin/permission-requests/virtual-group",
                Map.of("userId", userId, "virtualGroupId", virtualGroupId, "reason", reason != null ? reason : ""));
    }

    public Optional<Map<String, Object>> createBusinessUnitRequest(String userId, String businessUnitId, String reason) {
        return post("/api/v1/admin/permission-requests/business-unit",
                Map.of("userId", userId, "businessUnitId", businessUnitId, "reason", reason != null ? reason : ""));
    }

    public Optional<Map<String, Object>> cancelPermissionRequest(String requestId, String userId) {
        return post("/api/v1/admin/permission-requests/" + SafeUrlInput.requirePathToken(requestId) + "/cancel", Map.of("userId", userId));
    }

    public Optional<List<Map<String, Object>>> getUserPermissionRequests(String userId, String status) {
        String url = "/api/v1/admin/permission-requests?applicantId=" + SafeUrlInput.encodeQueryValue(userId);
        if (status != null) url += "&status=" + SafeUrlInput.encodeQueryValue(status);
        return getList(url);
    }

    // ==================== Approvals ====================

    public Optional<List<Map<String, Object>>> getPendingApprovals(String approverId) {
        return getList("/api/v1/admin/permission-requests?approverId=" + SafeUrlInput.encodeQueryValue(approverId) + "&status=PENDING");
    }

    public Optional<Map<String, Object>> approveRequest(String requestId, String approverId, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("approverId", approverId);
        if (comment != null) body.put("comment", comment);
        return post("/api/v1/admin/permission-requests/" + SafeUrlInput.requirePathToken(requestId) + "/approve", body);
    }

    public Optional<Map<String, Object>> rejectRequest(String requestId, String approverId, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("approverId", approverId);
        body.put("comment", comment);
        return post("/api/v1/admin/permission-requests/" + SafeUrlInput.requirePathToken(requestId) + "/reject", body);
    }

    public Optional<Map<String, Object>> checkIsApprover(String userId) {
        return get("/api/v1/admin/approvers/check?userId=" + SafeUrlInput.encodeQueryValue(userId));
    }

    public Optional<List<Map<String, Object>>> getApprovalHistory(String approverId, String status) {
        String url = "/api/v1/admin/permission-requests?approverId=" + SafeUrlInput.encodeQueryValue(approverId);
        if (status != null) url += "&status=" + SafeUrlInput.encodeQueryValue(status);
        return getList(url);
    }

    // ==================== Members ====================

    public Optional<List<Map<String, Object>>> getVirtualGroupMembers(String groupId) {
        return getList("/api/v1/admin/virtual-groups/" + SafeUrlInput.requirePathToken(groupId) + "/members");
    }

    public Optional<List<Map<String, Object>>> getBusinessUnitMembers(String businessUnitId) {
        return getList("/api/v1/admin/business-units/" + SafeUrlInput.requirePathToken(businessUnitId) + "/members");
    }

    // ==================== Exit ====================

    /**
     * 用户退出虚拟组（admin 返回 200 且无 body，须用 void 判断成功）。
     */
    public boolean exitVirtualGroup(String groupId, String userId) {
        return postVoid("/api/v1/admin/exit/virtual-groups/" + SafeUrlInput.requirePathToken(groupId) + "/users/" + SafeUrlInput.requirePathToken(userId), Map.of());
    }

    /**
     * 用户退出业务单元（同上）。
     */
    public boolean exitBusinessUnit(String businessUnitId, String userId) {
        return postVoid("/api/v1/admin/exit/business-units/" + SafeUrlInput.requirePathToken(businessUnitId) + "/users/" + SafeUrlInput.requirePathToken(userId), Map.of());
    }

    /** 审批人从虚拟组移除成员 */
    public boolean removeVirtualGroupMember(String groupId, String targetUserId) {
        return deleteVoid("/api/v1/admin/virtual-groups/" + SafeUrlInput.requirePathToken(groupId) + "/members/" + SafeUrlInput.requirePathToken(targetUserId));
    }

    /** 审批人从业务单元移除成员（整单元） */
    public boolean removeBusinessUnitMember(String unitId, String targetUserId) {
        return deleteVoid("/api/v1/admin/business-units/" + SafeUrlInput.requirePathToken(unitId) + "/members/" + SafeUrlInput.requirePathToken(targetUserId));
    }

    /** 移除用户在业务单元下的某一角色绑定 */
    public boolean removeUserBusinessUnitRole(String userId, String businessUnitId, String roleId) {
        return deleteVoid("/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/business-unit-roles/" + SafeUrlInput.requirePathToken(businessUnitId) + "/" + SafeUrlInput.requirePathToken(roleId));
    }

    public Optional<Map<String, Object>> getUserMemberships(String userId) {
        return get("/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/memberships");
    }

    // ==================== Permission Request Discovery ====================

    public Optional<List<Map<String, Object>>> getAvailableVirtualGroups() {
        return getList("/api/v1/admin/virtual-groups?hasApprovers=true");
    }

    public Optional<List<Map<String, Object>>> getApplicableBusinessUnits(String userId) {
        return getList("/api/v1/admin/permission-requests/applicable-business-units?userId=" + SafeUrlInput.encodeQueryValue(userId));
    }

    public Optional<List<Map<String, Object>>> getActivatableRoles(String businessUnitId, String userId) {
        return getList("/api/v1/admin/permission-requests/business-units/" + SafeUrlInput.requirePathToken(businessUnitId) + "/activatable-roles?userId=" + SafeUrlInput.encodeQueryValue(userId));
    }

    /**
     * FAIL-CLOSED(security): missing/failed admin-center response means no force-unclaim flags.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Boolean> evaluateForceUnclaim(String userId, List<Map<String, Object>> items) {
        if (userId == null || userId.isBlank() || items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("items", items);
        Optional<Map<String, Object>> data = post("/api/v1/admin/force-unclaim/evaluate", body);
        if (data.isEmpty()) {
            return Map.of();
        }
        Object flagsObj = data.get().get("flags");
        if (!(flagsObj instanceof Map<?, ?> rawFlags)) {
            return Map.of();
        }
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawFlags.entrySet()) {
            if (entry.getKey() != null) {
                flags.put(entry.getKey().toString(), Boolean.TRUE.equals(entry.getValue()));
            }
        }
        return flags;
    }

    // ==================== Internal HTTP helpers ====================

    private Optional<Map<String, Object>> get(String path) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    adminCenterUrl + path, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> raw = response.getBody();
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(raw));
        } catch (Exception e) {
            log.warn("Admin center GET {} failed: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<List<Map<String, Object>>> getList(String path) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    adminCenterUrl + path, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> raw = response.getBody();
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(ApiResponseBodyUnwrap.normalizeToListOfMaps(raw));
        } catch (Exception e) {
            log.warn("Admin center GET {} failed: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Map<String, Object>> post(String path, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    adminCenterUrl + path, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> raw = response.getBody();
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(raw));
        } catch (Exception e) {
            log.warn("Admin center POST {} failed: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean postVoid(String path, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Void> response = restTemplate.exchange(
                    adminCenterUrl + path, HttpMethod.POST, entity, Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Admin center POST {} failed: {}", path, e.getMessage());
            return false;
        }
    }

    private boolean deleteVoid(String path) {
        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    adminCenterUrl + path, HttpMethod.DELETE, null, Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Admin center DELETE {} failed: {}", path, e.getMessage());
            return false;
        }
    }
}
