package com.workflow.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin Center 客户端
 * 用于调用 admin-center 的 API 进行用户权限验证
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminCenterClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;
    
    /**
     * 检查用户是否是虚拟组成员
     * @param userId 用户ID
     * @param groupId 虚拟组ID
     * @return 是否是成员
     */
    public boolean isUserInVirtualGroup(String userId, String groupId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/virtual-groups/" + groupId + "/members";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> members = response.getBody();
            if (members == null || members.isEmpty()) {
                return false;
            }
            
            for (Map<String, Object> member : members) {
                if (userId.equals(member.get("userId"))) {
                    return true;
                }
            }
            return false;
            
        } catch (Exception e) {
            log.error("Failed to check if user {} is in virtual group {}: {}", userId, groupId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取用户所属的所有虚拟组ID
     * @param userId 用户ID
     * @return 虚拟组ID列表
     */
    public List<String> getUserVirtualGroupIds(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/virtual-groups";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> groups = response.getBody();
            if (groups == null || groups.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<String> groupIds = new ArrayList<>();
            for (Map<String, Object> group : groups) {
                // admin-center 返回的是 groupId 字段
                Object id = group.get("groupId");
                if (id == null) {
                    id = group.get("id");
                }
                if (id != null) {
                    groupIds.add(id.toString());
                }
            }
            return groupIds;
            
        } catch (Exception e) {
            log.error("Failed to get virtual groups for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户的角色列表
     * @param userId 用户ID
     * @return 角色编码列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getUserRoles(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/roles";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> roles = response.getBody();
            if (roles == null || roles.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<String> roleCodes = new ArrayList<>();
            for (Map<String, Object> role : roles) {
                Object code = role.get("code");
                if (code != null) {
                    roleCodes.add(code.toString());
                }
            }
            return roleCodes;
            
        } catch (Exception e) {
            log.error("Failed to get roles for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 检查 Admin Center 服务是否可用
     * @return 是否可用
     */
    public boolean isAvailable() {
        try {
            String url = adminCenterUrl + "/actuator/health";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> health = response.getBody();
            if (health != null && "UP".equals(health.get("status"))) {
                return true;
            }
            return false;
            
        } catch (Exception e) {
            log.warn("Admin Center is not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取用户详细信息
     * @param userId 用户ID（可以是ID或用户名）
     * @return 用户信息Map，包含 id, username, businessUnitId, entityManagerId, functionManagerId 等
     */
    public Map<String, Object> getUserInfo(String userId) {
        try {
            // 首先尝试通过ID查询
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("Failed to get user by ID {}, trying by username: {}", userId, e.getMessage());
        }
        
        // 尝试通过用户名搜索
        try {
            String searchUrl = adminCenterUrl + "/api/v1/admin/users?keyword=" + userId + "&size=1";
            ResponseEntity<Map<String, Object>> searchResponse = restTemplate.exchange(
                    searchUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> searchResult = searchResponse.getBody();
            if (searchResult != null && searchResult.get("content") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> users = (List<Map<String, Object>>) searchResult.get("content");
                if (!users.isEmpty()) {
                    return users.get(0);
                }
            }
        } catch (Exception e) {
            log.error("Failed to search user by username {}: {}", userId, e.getMessage());
        }
        
        return null;
    }
    
    // ==================== 任务分配相关 API ====================
    
    /**
     * 获取用户当前业务单元的 <strong>code</strong>（任务分配链路统一 code）。
     * @param userId 用户ID
     * @param activeBusinessUnitId 可选；多 BU 时须传入与用户 UBR 一致的当前业务单元 <strong>code</strong>
     * @return 业务单元 code，如果用户没有（唯一可确定的）业务单元则返回 null
     */
    public String getUserBusinessUnitId(String userId, String activeBusinessUnitId) {
        try {
            String base = adminCenterUrl + "/api/v1/admin/task-assignment/users/" + userId + "/business-unit";
            String url = base;
            if (activeBusinessUnitId != null && !activeBusinessUnitId.isBlank()) {
                url = base + "?activeBusinessUnitId=" + java.net.URLEncoder.encode(activeBusinessUnitId, java.nio.charset.StandardCharsets.UTF_8);
            }
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> result = response.getBody();
            if (result != null) {
                Object businessUnitId = result.get("businessUnitId");
                if (businessUnitId != null && !businessUnitId.toString().isEmpty()) {
                    return businessUnitId.toString();
                }
            }
            return null;
            
        } catch (Exception e) {
            log.error("Failed to get business unit ID for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    public String getUserBusinessUnitId(String userId) {
        return getUserBusinessUnitId(userId, null);
    }
    
    /**
     * 业务单元 id → code。供运行时把工作台上下文 {@code activeBusinessUnitId}（仍为 id）
     * 转成 code 再进入任务分配 code 链路。未找到返回 null。
     */
    public String getBusinessUnitCodeById(String businessUnitId) {
        if (businessUnitId == null || businessUnitId.isBlank()) {
            return null;
        }
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/business-units/by-id/"
                    + businessUnitId.trim() + "/code";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> result = response.getBody();
            if (result != null) {
                Object code = result.get("code");
                if (code != null && !code.toString().isEmpty()) {
                    return code.toString();
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get business unit code by id {}: {}", businessUnitId, e.getMessage());
            return null;
        }
    }

    /**
     * 获取父业务单元的 <strong>code</strong>（hierarchy 沿父链全程 code）。
     * @param businessUnitId 业务单元 code
     * @return 父业务单元 code，如果没有父级则返回 null
     */
    public String getParentBusinessUnitId(String businessUnitId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/business-units/" + businessUnitId + "/parent";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> result = response.getBody();
            if (result != null) {
                Object parentId = result.get("parentBusinessUnitId");
                if (parentId != null && !parentId.toString().isEmpty()) {
                    return parentId.toString();
                }
            }
            return null;
            
        } catch (Exception e) {
            log.error("Failed to get parent business unit ID for {}: {}", businessUnitId, e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取业务单元中拥有指定角色的用户ID列表
     * @param businessUnitId 业务单元 code
     * @param roleId 角色 code（BU_BOUNDED类型）
     * @return 用户ID列表
     */
    public List<String> getUsersByBusinessUnitAndRole(String businessUnitId, String roleId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/business-units/" + businessUnitId + "/roles/" + roleId + "/users";
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            
            List<String> userIds = response.getBody();
            return userIds != null ? userIds : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get users by business unit {} and role {}: {}", businessUnitId, roleId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 自某 BU 起沿父链向上，收集各层 BU 中拥有指定角色的用户 ID（并集、保序去重）。
     * @param startBusinessUnitId 起始 BU code
     * @param roleId 角色 code
     */
    public List<String> collectUserIdsForRoleInBusinessUnitHierarchy(String startBusinessUnitId, String roleId) {
        if (startBusinessUnitId == null || startBusinessUnitId.isBlank() || roleId == null || roleId.isBlank()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> union = new LinkedHashSet<>();
        String bu = startBusinessUnitId.trim();
        int guard = 0;
        final int maxHops = 256;
        while (bu != null && !bu.isEmpty() && guard++ < maxHops) {
            List<String> chunk = getUsersByBusinessUnitAndRole(bu, roleId.trim());
            if (chunk != null) {
                union.addAll(chunk);
            }
            bu = getParentBusinessUnitId(bu);
        }
        return new ArrayList<>(union);
    }
    
    /**
     * 获取拥有指定BU无关型角色的用户ID列表
     * @param roleId 角色 code（BU_UNBOUNDED类型）
     * @return 用户ID列表
     */
    public List<String> getUsersByUnboundedRole(String roleId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/roles/" + roleId + "/users";
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            
            List<String> userIds = response.getBody();
            return userIds != null ? userIds : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get users by unbounded role {}: {}", roleId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按虚拟组编码（BPMN VIRTUAL_GROUP 的 assigneeValue，如 DOCUMENT_VERIFIERS）获取成员用户 ID。
     */
    public List<String> getUsersByVirtualGroupCode(String code) {
        if (code == null || code.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String url = UriComponentsBuilder
                    .fromUriString(adminCenterUrl + "/api/v1/admin/task-assignment/virtual-groups/by-code/{code}/users")
                    .buildAndExpand(code.trim())
                    .toUriString();
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            List<String> userIds = response.getBody();
            return userIds != null ? userIds : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get users by virtual group code {}: {}", code, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取业务单元的准入角色 code 列表
     * @param businessUnitId 业务单元 code
     * @return 角色 code 列表
     */
    public List<String> getEligibleRoleIds(String businessUnitId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/business-units/" + businessUnitId + "/eligible-roles";
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            
            List<String> roleIds = response.getBody();
            return roleIds != null ? roleIds : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get eligible role IDs for business unit {}: {}", businessUnitId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 检查角色是否是业务单元的准入角色
     * @param businessUnitId 业务单元 code
     * @param roleId 角色 code
     * @return 是否是准入角色
     */
    public boolean isEligibleRole(String businessUnitId, String roleId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/business-units/" + businessUnitId + "/roles/" + roleId + "/eligible";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> result = response.getBody();
            if (result != null) {
                Object eligible = result.get("eligible");
                return Boolean.TRUE.equals(eligible);
            }
            return false;
            
        } catch (Exception e) {
            log.error("Failed to check if role {} is eligible for business unit {}: {}", roleId, businessUnitId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取所有BU绑定型角色
     * @return 角色列表
     */
    public List<Map<String, Object>> getBuBoundedRoles() {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/roles/bu-bounded";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> roles = response.getBody();
            return roles != null ? roles : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get BU bounded roles: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取所有BU无关型角色
     * @return 角色列表
     */
    public List<Map<String, Object>> getBuUnboundedRoles() {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/roles/bu-unbounded";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> roles = response.getBody();
            return roles != null ? roles : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get BU unbounded roles: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取功能单元邮件连接凭据（内部 API）
     */
    public Optional<Map<String, Object>> getEmailConnectionCredentials(String functionUnitId, String connectionId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/internal/function-units/"
                    + functionUnitId + "/connections/" + connectionId + "/credentials";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get email connection credentials for {} / {}: {}",
                    functionUnitId, connectionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 按功能单元 code 解析 Admin Center 中的功能单元 ID
     */
    public Optional<String> resolveFunctionUnitIdByCode(String functionUnitCode) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/internal/function-units/by-code/"
                    + functionUnitCode + "/id";
            ResponseEntity<Map<String, String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, String>>() {}
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.ofNullable(response.getBody().get("functionUnitId"));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to resolve function unit id by code {}: {}", functionUnitCode, e.getMessage());
            return Optional.empty();
        }
    }

}
