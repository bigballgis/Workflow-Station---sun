package com.portal.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
        return post("/api/v1/admin/permission-requests/" + requestId + "/cancel", Map.of("userId", userId));
    }

    public Optional<List<Map<String, Object>>> getUserPermissionRequests(String userId, String status) {
        String url = "/api/v1/admin/permission-requests?applicantId=" + userId;
        if (status != null) url += "&status=" + status;
        return getList(url);
    }

    // ==================== Approvals ====================

    public Optional<List<Map<String, Object>>> getPendingApprovals(String approverId) {
        return getList("/api/v1/admin/permission-requests?approverId=" + approverId + "&status=PENDING");
    }

    public Optional<Map<String, Object>> approveRequest(String requestId, String approverId, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("approverId", approverId);
        if (comment != null) body.put("comment", comment);
        return post("/api/v1/admin/permission-requests/" + requestId + "/approve", body);
    }

    public Optional<Map<String, Object>> rejectRequest(String requestId, String approverId, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("approverId", approverId);
        body.put("comment", comment);
        return post("/api/v1/admin/permission-requests/" + requestId + "/reject", body);
    }

    public Optional<Map<String, Object>> checkIsApprover(String userId) {
        return get("/api/v1/admin/approvers/check?userId=" + userId);
    }

    public Optional<List<Map<String, Object>>> getApprovalHistory(String approverId, String status) {
        String url = "/api/v1/admin/permission-requests?approverId=" + approverId;
        if (status != null) url += "&status=" + status;
        return getList(url);
    }

    // ==================== Members ====================

    public Optional<List<Map<String, Object>>> getVirtualGroupMembers(String groupId) {
        return getList("/api/v1/admin/virtual-groups/" + groupId + "/members");
    }

    public Optional<List<Map<String, Object>>> getBusinessUnitMembers(String businessUnitId) {
        return getList("/api/v1/admin/business-units/" + businessUnitId + "/members");
    }

    public Optional<Map<String, Object>> getApprovalScope(String userId) {
        return get("/api/v1/admin/approvers/scope?userId=" + userId);
    }

    // ==================== Exit ====================

    /**
     * 用户退出虚拟组（admin 返回 200 且无 body，须用 void 判断成功）。
     */
    public boolean exitVirtualGroup(String groupId, String userId) {
        return postVoid("/api/v1/admin/exit/virtual-groups/" + groupId + "/users/" + userId, Map.of());
    }

    /**
     * 用户退出业务单元（同上）。
     */
    public boolean exitBusinessUnit(String businessUnitId, String userId) {
        return postVoid("/api/v1/admin/exit/business-units/" + businessUnitId + "/users/" + userId, Map.of());
    }

    /** 审批人从虚拟组移除成员 */
    public boolean removeVirtualGroupMember(String groupId, String targetUserId) {
        return deleteVoid("/api/v1/admin/virtual-groups/" + groupId + "/members/" + targetUserId);
    }

    /** 审批人从业务单元移除成员（整单元） */
    public boolean removeBusinessUnitMember(String unitId, String targetUserId) {
        return deleteVoid("/api/v1/admin/business-units/" + unitId + "/members/" + targetUserId);
    }

    /** 移除用户在业务单元下的某一角色绑定 */
    public boolean removeUserBusinessUnitRole(String userId, String businessUnitId, String roleId) {
        return deleteVoid("/api/v1/admin/users/" + userId + "/business-unit-roles/" + businessUnitId + "/" + roleId);
    }

    public Optional<Map<String, Object>> getUserMemberships(String userId) {
        return get("/api/v1/admin/users/" + userId + "/memberships");
    }

    public Optional<List<Map<String, Object>>> getExitHistory(String userId) {
        return getList("/api/v1/admin/member-change-logs?userId=" + userId + "&changeType=EXIT");
    }

    // ==================== Permission Request Discovery ====================

    public Optional<List<Map<String, Object>>> getAvailableVirtualGroups() {
        return getList("/api/v1/admin/virtual-groups?hasApprovers=true");
    }

    public Optional<List<Map<String, Object>>> getApplicableBusinessUnits(String userId) {
        return getList("/api/v1/admin/permission-requests/applicable-business-units?userId=" + userId);
    }

    public Optional<List<Map<String, Object>>> getActivatableRoles(String businessUnitId, String userId) {
        return getList("/api/v1/admin/permission-requests/business-units/" + businessUnitId + "/activatable-roles?userId=" + userId);
    }

    // ==================== Internal HTTP helpers ====================

    private Optional<Map<String, Object>> get(String path) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    adminCenterUrl + path, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.warn("Admin center GET {} failed: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<List<Map<String, Object>>> getList(String path) {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    adminCenterUrl + path, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            return Optional.ofNullable(response.getBody());
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
            return Optional.ofNullable(response.getBody());
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
