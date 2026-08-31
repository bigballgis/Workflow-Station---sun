package com.portal.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.platform.common.constant.PlatformConstants;
import com.platform.common.util.ApiResponseBodyUnwrap;
import com.platform.common.util.SafeUrlInput;
import com.platform.security.util.SecurityContextUtils;

import java.util.*;

/**
 * 虚拟组访问组件
 * 调用 Admin Center API 获取和管理虚拟组
 *
 * <p>FALLBACK(external) — class-wide policy: every method here is a cross-service HTTP call
 * whose catch degrades to empty/null/false. Queries fail CLOSED (no data = no permission,
 * fewer features rather than wrong data); mutations return false and callers surface the
 * error to the user. This component is NOT on the task-assignment authoritative path (that
 * is the engine's AdminCenterClient, which throws AdminCenterUnavailableException); the one
 * hot consumer, WorkspaceTaskFilterComponent#getUserVirtualGroups, additionally has
 * last-known-good caching + a 503 surface for the cold-start-plus-outage case.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualGroupAccessComponent {
    
    private final RestTemplate restTemplate;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    /**
     * C-3 (docs/ap-integration/DECISIONS.md#d6): shared secret marking this as a trusted
     * first-party service call, required for admin-center to honor the forwarded X-User-Id.
     */
    @Value("${service.internal-token:}")
    private String serviceInternalToken;

    /**
     * 获取所有虚拟组列表
     */
    public List<Map<String, Object>> getVirtualGroups() {
        try {
            String url = adminCenterUrl + "/api/v1/admin/virtual-groups";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get virtual groups: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取虚拟组详情
     */
    public Map<String, Object> getVirtualGroupById(String groupId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/virtual-groups/" + SafeUrlInput.requirePathToken(groupId);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            return response.getBody() != null ? ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()) : null;
            
        } catch (Exception e) {
            log.error("Failed to get virtual group {}: {}", groupId, e.getMessage());
            return null;
        }
    }

    /**
     * 虚拟组绑定的业务角色主键（admin-center {@code GET /virtual-groups/{groupId}/role}）。
     */
    public Optional<String> getBoundRoleIdForVirtualGroup(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = adminCenterUrl + "/api/v1/admin/virtual-groups/" + SafeUrlInput.requirePathToken(groupId) + "/role";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getStatusCode() == HttpStatus.NO_CONTENT || response.getBody() == null) {
                return Optional.empty();
            }
            Object id = response.getBody().get("id");
            return id != null ? Optional.of(String.valueOf(id)) : Optional.empty();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NO_CONTENT) {
                return Optional.empty();
            }
            log.debug("getBoundRoleIdForVirtualGroup: groupId={} status={}", groupId, e.getStatusCode());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("getBoundRoleIdForVirtualGroup failed for {}: {}", groupId, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 获取用户当前的虚拟组成员身份
     */
    public List<Map<String, Object>> getUserVirtualGroups(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/virtual-groups";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get user virtual groups for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 添加用户到虚拟组
     * @param userId 用户ID
     * @param groupId 虚拟组ID
     * @param reason 加入原因
     * @return 是否成功
     */
    public boolean addUserToVirtualGroup(String userId, String groupId, String reason) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/virtual-groups/" + SafeUrlInput.requirePathToken(groupId) + "/members";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("userId", userId);
            requestBody.put("role", "MEMBER");  // 默认角色为成员
            if (reason != null && !reason.isEmpty()) {
                requestBody.put("reason", reason);
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, mutationHeaders());
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.error("Failed to add user {} to virtual group {}: {}", userId, groupId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取虚拟组的成员列表
     */
    public List<Map<String, Object>> getVirtualGroupMembers(String groupId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/virtual-groups/" + SafeUrlInput.requirePathToken(groupId) + "/members";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get virtual group members for group {}: {}", groupId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 检查用户是否是虚拟组成员
     */
    public boolean isUserInVirtualGroup(String userId, String groupId) {
        List<Map<String, Object>> members = getVirtualGroupMembers(groupId);
        for (Map<String, Object> member : members) {
            if (userId.equals(member.get("userId"))) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取虚拟组绑定的角色列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getVirtualGroupBoundRoles(String groupId) {
        Map<String, Object> group = getVirtualGroupById(groupId);
        if (group != null && group.containsKey("boundRoles")) {
            Object boundRoles = group.get("boundRoles");
            if (boundRoles instanceof List) {
                return (List<Map<String, Object>>) boundRoles;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Find the virtual group bound to the given roleId.
     * Admin-center virtual group list responses typically expose `boundRoleId` and `id`.
     */
    public String getVirtualGroupIdByBoundRoleId(String roleId) {
        if (roleId == null || roleId.isBlank()) return null;
        try {
            List<Map<String, Object>> groups = getVirtualGroups();
            for (Map<String, Object> g : groups) {
                Object boundRoleId = g.get("boundRoleId");
                if (boundRoleId != null && roleId.equals(String.valueOf(boundRoleId))) {
                    Object groupId = g.get("id");
                    return groupId != null ? String.valueOf(groupId) : null;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to find virtual group by boundRoleId {}: {}", roleId, e.getMessage());
            return null;
        }
    }
    
    // ========== 业务单元相关方法 ==========
    
    /**
     * 获取所有业务单元列表（从树形结构扁平化）
     */
    public List<Map<String, Object>> getBusinessUnits() {
        try {
            String url = adminCenterUrl + "/api/v1/admin/business-units/tree";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> tree = response.getBody();
            if (tree == null) {
                return Collections.emptyList();
            }
            
            // 扁平化树形结构
            List<Map<String, Object>> flatList = new ArrayList<>();
            flattenBusinessUnitTree(tree, flatList);
            return flatList;
            
        } catch (Exception e) {
            log.error("Failed to get business units: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 业务单元树（保留 children 层级，供级联选择器使用）。
     */
    public List<Map<String, Object>> getBusinessUnitsTree() {
        try {
            String url = adminCenterUrl + "/api/v1/admin/business-units/tree";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> tree = response.getBody();
            return tree != null ? tree : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get business unit tree: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 递归扁平化业务单元树
     */
    @SuppressWarnings("unchecked")
    private void flattenBusinessUnitTree(List<Map<String, Object>> tree, List<Map<String, Object>> flatList) {
        for (Map<String, Object> node : tree) {
            // 添加当前节点（不包含children）
            Map<String, Object> flatNode = new HashMap<>(node);
            flatNode.remove("children");
            flatList.add(flatNode);
            
            // 递归处理子节点
            Object children = node.get("children");
            if (children instanceof List) {
                flattenBusinessUnitTree((List<Map<String, Object>>) children, flatList);
            }
        }
    }
    
    /**
     * 获取业务单元详情
     */
    public Map<String, Object> getBusinessUnitById(String businessUnitId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/business-units/" + SafeUrlInput.requirePathToken(businessUnitId);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            return response.getBody() != null ? ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()) : null;
            
        } catch (Exception e) {
            log.error("Failed to get business unit {}: {}", businessUnitId, e.getMessage());
            return null;
        }
    }

    /**
     * 业务单元已绑定的业务角色（与 admin {@code GET /business-units/{unitId}/roles} 对齐）
     */
    public List<Map<String, Object>> getBusinessUnitBoundRoles(String businessUnitId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/business-units/" + SafeUrlInput.requirePathToken(businessUnitId) + "/roles";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get bound roles for business unit {}: {}", businessUnitId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户当前的业务单元成员身份
     */
    public List<Map<String, Object>> getUserBusinessUnits(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/business-units";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get user business units for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 检查用户是否是业务单元成员
     */
    public boolean isUserInBusinessUnit(String userId, String businessUnitId) {
        List<Map<String, Object>> userBusinessUnits = getUserBusinessUnits(userId);
        for (Map<String, Object> bu : userBusinessUnits) {
            if (businessUnitId.equals(bu.get("id")) || businessUnitId.equals(bu.get("businessUnitId"))) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 添加用户到业务单元
     * @param userId 用户ID
     * @param businessUnitId 业务单元ID
     * @param reason 加入原因
     * @return 是否成功
     */
    public boolean addUserToBusinessUnit(String userId, String businessUnitId, String reason) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/business-units/" + SafeUrlInput.requirePathToken(businessUnitId) + "/members";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("userId", userId);
            if (reason != null && !reason.isEmpty()) {
                requestBody.put("reason", reason);
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, mutationHeaders());
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.error("Failed to add user {} to business unit {}: {}", userId, businessUnitId, e.getMessage());
            return false;
        }
    }

    /**
     * 为用户在指定业务单元下分配业务角色（Eligible Role 绑定）
     */
    public boolean assignUserBusinessUnitRole(String userId, String businessUnitId, String roleId) {
        return assignUserBusinessUnitRole(userId, businessUnitId, roleId, com.platform.security.ubr.UbrMembershipType.MEMBER);
    }

    public boolean assignUserBusinessUnitRole(String userId, String businessUnitId, String roleId, String membershipType) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/business-unit-roles";
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("businessUnitId", businessUnitId);
            requestBody.put("roleId", roleId);
            requestBody.put("membershipType", com.platform.security.ubr.UbrMembershipType.normalize(membershipType));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, mutationHeaders());

            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Void.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to assign BU role: user={}, bu={}, role={}: {}",
                    userId, businessUnitId, roleId, e.getMessage());
            return false;
        }
    }
    
    // ========== 审批人相关方法 ==========
    
    /**
     * 检查用户是否是虚拟组的审批人
     */
    public boolean isApproverForVirtualGroup(String userId, String groupId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/approvers/check?userId=" + SafeUrlInput.encodeQueryValue(userId)
                    + "&targetType=VIRTUAL_GROUP&targetId=" + SafeUrlInput.encodeQueryValue(groupId);
            ResponseEntity<Boolean> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    Boolean.class
            );
            return Boolean.TRUE.equals(response.getBody());
        } catch (Exception e) {
            log.error("Failed to check if user {} is approver for virtual group {}: {}", userId, groupId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查用户是否是业务单元的审批人
     */
    public boolean isApproverForBusinessUnit(String userId, String businessUnitId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/approvers/check?userId=" + SafeUrlInput.encodeQueryValue(userId)
                    + "&targetType=BUSINESS_UNIT&targetId=" + SafeUrlInput.encodeQueryValue(businessUnitId);
            ResponseEntity<Boolean> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    Boolean.class
            );
            return Boolean.TRUE.equals(response.getBody());
        } catch (Exception e) {
            log.error("Failed to check if user {} is approver for business unit {}: {}", userId, businessUnitId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取用户作为审批人的所有虚拟组ID
     */
    public List<String> getApproverVirtualGroupIds(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/approvers/user/" + SafeUrlInput.requirePathToken(userId) + "/virtual-groups";
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get approver virtual groups for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户作为审批人的所有业务单元ID
     */
    public List<String> getApproverBusinessUnitIds(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/approvers/user/" + SafeUrlInput.requirePathToken(userId) + "/business-units";
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get approver business units for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 用户全部业务单元角色分配（与 admin {@code GET /users/{userId}/business-unit-roles} 对齐）
     */
    public List<Map<String, Object>> listAllUserBusinessUnitRoles(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/business-unit-roles";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to list all BU roles for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 用户是否在任意业务单元下仍持有指定角色
     */
    public boolean userHasBusinessUnitRoleAnywhere(String userId, String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return false;
        }
        return listAllUserBusinessUnitRoles(userId).stream()
                .anyMatch(m -> roleId.equals(String.valueOf(m.get("roleId"))));
    }

    /**
     * 将用户从虚拟组移除（与 admin {@code DELETE /virtual-groups/{id}/members/{userId}} 对齐）
     */
    public boolean removeUserFromVirtualGroup(String userId, String groupId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/virtual-groups/" + SafeUrlInput.requirePathToken(groupId) + "/members/" + SafeUrlInput.requirePathToken(userId);
            ResponseEntity<Void> response = restTemplate.exchange(
                    url, HttpMethod.DELETE, new HttpEntity<>(mutationHeaders()), Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to remove user {} from virtual group {}: {}", userId, groupId, e.getMessage());
            return false;
        }
    }

    /**
     * 当用户在任何 BU 下都不再持有某角色时，从该角色绑定的虚拟组中移除，以便 admin 「角色 → Role Members」与 BU 角色分配一致。
     */
    public void removeFromBoundVirtualGroupIfNoBuRoleAssignmentRemaining(String userId, String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return;
        }
        if (userHasBusinessUnitRoleAnywhere(userId, roleId)) {
            return;
        }
        String vgId = getVirtualGroupIdByBoundRoleId(roleId);
        if (vgId == null || vgId.isBlank()) {
            return;
        }
        if (!isUserInVirtualGroup(userId, vgId)) {
            return;
        }
        boolean ok = removeUserFromVirtualGroup(userId, vgId);
        if (ok) {
            log.info("Removed user {} from virtual group {} (no BU assignments left for role {})", userId, vgId, roleId);
        } else {
            log.warn("Failed to remove user {} from virtual group {} after role {} had no BU assignments left", userId, vgId, roleId);
        }
    }

    /**
     * 用户在指定业务单元下已分配的业务角色列表（与 admin {@code GET .../by-business-unit/{buId}} 对齐）
     */
    public List<Map<String, Object>> listUserBusinessUnitRolesInBusinessUnit(String userId, String businessUnitId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId)
                    + "/business-unit-roles/by-business-unit/" + SafeUrlInput.requirePathToken(businessUnitId);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to list BU roles for user {} in unit {}: {}", userId, businessUnitId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 用户是否拥有指定业务单元下的某业务角色
     */
    public boolean userHasBusinessUnitRole(String userId, String businessUnitId, String roleId) {
        return listUserBusinessUnitRolesInBusinessUnit(userId, businessUnitId).stream()
                .anyMatch(m -> roleId != null && roleId.equals(String.valueOf(m.get("roleId"))));
    }

    /**
     * 移除用户在业务单元下的某一业务角色绑定（审批通过时调用，与 admin DELETE 对齐）
     */
    public boolean removeUserBusinessUnitRole(String userId, String businessUnitId, String roleId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/business-unit-roles/"
                    + SafeUrlInput.requirePathToken(businessUnitId) + "/" + SafeUrlInput.requirePathToken(roleId);
            ResponseEntity<Void> response = restTemplate.exchange(
                    url, HttpMethod.DELETE, new HttpEntity<>(mutationHeaders()), Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to remove BU role: user={}, bu={}, role={}: {}",
                    userId, businessUnitId, roleId, e.getMessage());
            return false;
        }
    }

    /**
     * 用户退出业务单元（与 admin {@code POST /exit/business-units/{buId}/users/{userId}} 对齐）。
     * 会移除成员关系并清理该 BU 下剩余的角色绑定。
     */
    public boolean exitBusinessUnit(String userId, String businessUnitId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/exit/business-units/" + SafeUrlInput.requirePathToken(businessUnitId) + "/users/" + SafeUrlInput.requirePathToken(userId);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of(), mutationHeaders());
            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to exit business unit: user={}, bu={}: {}", userId, businessUnitId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查用户是否是任何目标的审批人
     */
    public boolean isAnyApprover(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/approvers/user/" + SafeUrlInput.requirePathToken(userId) + "/is-any";
            ResponseEntity<Boolean> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    Boolean.class
            );
            return Boolean.TRUE.equals(response.getBody());
        } catch (Exception e) {
            log.error("Failed to check if user {} is any approver: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * C-3 headers so admin-center honors the call as a first-party write
     * ({@code OrganizationMutationAccessInterceptor} otherwise denies empty-role principals).
     */
    private HttpHeaders mutationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        SecurityContextUtils.getCurrentUserId().ifPresent(id -> headers.set(PlatformConstants.HEADER_USER_ID, id));
        SecurityContextUtils.getCurrentUsername().ifPresent(name -> headers.set("X-Username", name));
        if (serviceInternalToken != null && !serviceInternalToken.isBlank()) {
            headers.set(PlatformConstants.HEADER_SERVICE_TOKEN, serviceInternalToken);
        }
        return headers;
    }
}
