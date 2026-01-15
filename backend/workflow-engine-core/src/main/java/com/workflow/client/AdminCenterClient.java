package com.workflow.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

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
     * 检查用户是否拥有指定部门的指定角色
     * @param userId 用户ID
     * @param departmentId 部门ID
     * @param roleCode 角色编码
     * @return 是否拥有该部门角色
     */
    public boolean hasUserDepartmentRole(String userId, String departmentId, String roleCode) {
        try {
            // 首先获取用户信息，包括部门
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + userId;
            ResponseEntity<Map<String, Object>> userResponse = restTemplate.exchange(
                    userUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> user = userResponse.getBody();
            if (user == null) {
                return false;
            }
            
            // 检查用户是否属于指定部门
            String userDeptId = (String) user.get("departmentId");
            if (departmentId != null && !departmentId.equals(userDeptId)) {
                // 用户不属于指定部门，检查是否属于子部门
                if (!isUserInDepartmentHierarchy(userId, departmentId)) {
                    return false;
                }
            }
            
            // 获取用户的角色列表
            List<String> userRoles = getUserRoles(userId);
            return userRoles.contains(roleCode);
            
        } catch (Exception e) {
            log.error("Failed to check department role for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查用户是否在部门层级中（包括子部门）
     * @param userId 用户ID
     * @param departmentId 部门ID
     * @return 是否在部门层级中
     */
    public boolean isUserInDepartmentHierarchy(String userId, String departmentId) {
        try {
            // 获取用户信息
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + userId;
            ResponseEntity<Map<String, Object>> userResponse = restTemplate.exchange(
                    userUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> user = userResponse.getBody();
            if (user == null) {
                return false;
            }
            
            String userDeptId = (String) user.get("departmentId");
            if (userDeptId == null) {
                return false;
            }
            
            // 如果用户直接属于该部门
            if (departmentId.equals(userDeptId)) {
                return true;
            }
            
            // 检查用户部门是否是目标部门的子部门
            return isDepartmentDescendant(userDeptId, departmentId);
            
        } catch (Exception e) {
            log.error("Failed to check department hierarchy for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查部门是否是另一个部门的后代
     * @param childDeptId 子部门ID
     * @param parentDeptId 父部门ID
     * @return 是否是后代
     */
    private boolean isDepartmentDescendant(String childDeptId, String parentDeptId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/departments/" + childDeptId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> dept = response.getBody();
            if (dept == null) {
                return false;
            }
            
            String currentParentId = (String) dept.get("parentId");
            while (currentParentId != null) {
                if (parentDeptId.equals(currentParentId)) {
                    return true;
                }
                
                // 获取父部门信息
                String parentUrl = adminCenterUrl + "/api/v1/admin/departments/" + currentParentId;
                ResponseEntity<Map<String, Object>> parentResponse = restTemplate.exchange(
                        parentUrl,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );
                
                Map<String, Object> parentDept = parentResponse.getBody();
                if (parentDept == null) {
                    break;
                }
                currentParentId = (String) parentDept.get("parentId");
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Failed to check department hierarchy: {}", e.getMessage());
            return false;
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
     * 获取用户的部门角色列表（格式：departmentId:roleCode）
     * @param userId 用户ID
     * @return 部门角色列表
     */
    public List<String> getUserDepartmentRoles(String userId) {
        try {
            // 获取用户信息
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + userId;
            ResponseEntity<Map<String, Object>> userResponse = restTemplate.exchange(
                    userUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> user = userResponse.getBody();
            if (user == null) {
                return Collections.emptyList();
            }
            
            String departmentId = (String) user.get("departmentId");
            if (departmentId == null) {
                return Collections.emptyList();
            }
            
            // 获取用户角色
            List<String> roles = getUserRoles(userId);
            
            // 组合部门角色
            List<String> deptRoles = new ArrayList<>();
            for (String role : roles) {
                deptRoles.add(departmentId + ":" + role);
            }
            
            return deptRoles;
            
        } catch (Exception e) {
            log.error("Failed to get department roles for user {}: {}", userId, e.getMessage());
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
     * @return 用户信息Map，包含 id, username, departmentId, entityManagerId, functionManagerId 等
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
    
    /**
     * 获取部门信息
     * @param departmentId 部门ID
     * @return 部门信息Map，包含 id, name, parentId, managerId 等
     */
    public Map<String, Object> getDepartmentInfo(String departmentId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/departments/" + departmentId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get department info for {}: {}", departmentId, e.getMessage());
        }
        return null;
    }
    
    /**
     * 获取部门成员ID列表
     * @param departmentId 部门ID
     * @return 成员用户ID列表
     */
    public List<String> getDepartmentMembers(String departmentId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/departments/" + departmentId + "/members";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            List<Map<String, Object>> members = response.getBody();
            if (members == null || members.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<String> memberIds = new ArrayList<>();
            for (Map<String, Object> member : members) {
                Object id = member.get("id");
                if (id == null) {
                    id = member.get("userId");
                }
                if (id != null) {
                    memberIds.add(id.toString());
                }
            }
            return memberIds;
            
        } catch (Exception e) {
            log.error("Failed to get department members for {}: {}", departmentId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取虚拟组成员ID列表
     * @param groupId 虚拟组ID
     * @return 成员用户ID列表
     */
    public List<String> getVirtualGroupMembers(String groupId) {
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
                return Collections.emptyList();
            }
            
            List<String> memberIds = new ArrayList<>();
            for (Map<String, Object> member : members) {
                Object id = member.get("userId");
                if (id == null) {
                    id = member.get("id");
                }
                if (id != null) {
                    memberIds.add(id.toString());
                }
            }
            return memberIds;
            
        } catch (Exception e) {
            log.error("Failed to get virtual group members for {}: {}", groupId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    // ==================== 任务分配相关 API ====================
    
    /**
     * 获取用户的业务单元ID
     * @param userId 用户ID
     * @return 业务单元ID，如果用户没有业务单元则返回null
     */
    public String getUserBusinessUnitId(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/task-assignment/users/" + userId + "/business-unit";
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
    
    /**
     * 获取业务单元的父业务单元ID
     * @param businessUnitId 业务单元ID
     * @return 父业务单元ID，如果没有父级则返回null
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
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID（BU_BOUNDED类型）
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
     * 获取拥有指定BU无关型角色的用户ID列表
     * @param roleId 角色ID（BU_UNBOUNDED类型）
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
     * 获取业务单元的准入角色ID列表
     * @param businessUnitId 业务单元ID
     * @return 角色ID列表
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
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID
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
}
