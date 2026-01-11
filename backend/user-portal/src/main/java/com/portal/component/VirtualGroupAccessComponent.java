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
}
