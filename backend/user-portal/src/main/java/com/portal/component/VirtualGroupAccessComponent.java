package com.portal.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 虚拟组访问组件
 * 调用 Admin Center API 获取和管理虚拟组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualGroupAccessComponent {
    
    private final RestTemplate restTemplate;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;
    
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
            String url = adminCenterUrl + "/api/v1/admin/virtual-groups/" + groupId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Failed to get virtual group {}: {}", groupId, e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取用户当前的虚拟组成员身份
     */
    public List<Map<String, Object>> getUserVirtualGroups(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/virtual-groups";
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
            String url = adminCenterUrl + "/api/v1/admin/virtual-groups/" + groupId + "/members";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("userId", userId);
            requestBody.put("role", "MEMBER");  // 默认角色为成员
            if (reason != null && !reason.isEmpty()) {
                requestBody.put("reason", reason);
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
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
            String url = adminCenterUrl + "/api/v1/admin/virtual-groups/" + groupId + "/members";
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
            String url = adminCenterUrl + "/api/v1/admin/business-units/" + businessUnitId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Failed to get business unit {}: {}", businessUnitId, e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取用户当前的业务单元成员身份
     */
    public List<Map<String, Object>> getUserBusinessUnits(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/business-units";
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
            String url = adminCenterUrl + "/api/v1/admin/business-units/" + businessUnitId + "/members";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("userId", userId);
            if (reason != null && !reason.isEmpty()) {
                requestBody.put("reason", reason);
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
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
    
    // ========== 审批人相关方法 ==========
    
    /**
     * 检查用户是否是虚拟组的审批人
     */
    public boolean isApproverForVirtualGroup(String userId, String groupId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/approvers/check?userId=" + userId 
                    + "&targetType=VIRTUAL_GROUP&targetId=" + groupId;
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
            String url = adminCenterUrl + "/api/v1/admin/approvers/check?userId=" + userId 
                    + "&targetType=BUSINESS_UNIT&targetId=" + businessUnitId;
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
            String url = adminCenterUrl + "/api/v1/admin/approvers/user/" + userId + "/virtual-groups";
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
            String url = adminCenterUrl + "/api/v1/admin/approvers/user/" + userId + "/business-units";
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
     * 检查用户是否是任何目标的审批人
     */
    public boolean isAnyApprover(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/approvers/user/" + userId + "/is-any";
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
}
