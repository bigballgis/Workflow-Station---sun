package com.portal.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.platform.common.util.ApiResponseBodyUnwrap;
import com.portal.dto.PageResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 角色访问组件
 * 调用 Admin Center API 获取和管理角色
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleAccessComponent {
    
    private final RestTemplate restTemplate;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;
    
    /**
     * 获取所有业务角色列表
     */
    public List<Map<String, Object>> getBusinessRoles() {
        try {
            String url = adminCenterUrl + "/api/v1/admin/roles/business";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get business roles: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户当前的角色列表
     */
    public List<Map<String, Object>> getUserRoles(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/roles";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get user roles for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户当前的业务角色列表
     */
    public List<Map<String, Object>> getUserBusinessRoles(String userId) {
        try {
            // Get all user roles (no type filter - let frontend handle filtering)
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/roles";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get user business roles for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 分配角色给用户
     * @param userId 用户ID
     * @param roleId 角色ID
     * @param operatedBy 操作人ID（通常是系统自动操作，使用 userId）
     * @param reason 分配原因
     * @return 是否成功
     */
    public boolean assignRoleToUser(String userId, String roleId, String operatedBy, String reason) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/roles/" + roleId + "/members/" + userId;
            if (reason != null && !reason.isEmpty()) {
                url += "?reason=" + reason;
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", operatedBy != null ? operatedBy : userId);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Void.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.error("Failed to assign role {} to user {}: {}", roleId, userId, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取角色详情
     */
    public Map<String, Object> getRoleById(String roleId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/roles/" + roleId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            return response.getBody() != null ? ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()) : null;
            
        } catch (Exception e) {
            log.error("Failed to get role {}: {}", roleId, e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取用户详情
     */
    public Map<String, Object> getUserById(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            return response.getBody() != null ? ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()) : null;
            
        } catch (Exception e) {
            log.error("Failed to get user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 门户选人：仅 ACTIVE 用户，供代办申请搜索（Admin GET /users）
     */
    @SuppressWarnings("unchecked")
    public PageResponse<Map<String, Object>> searchActiveUsersForPortal(String keyword, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 50);
        try {
            StringBuilder url = new StringBuilder(adminCenterUrl + "/api/v1/admin/users?status=ACTIVE&page=")
                    .append(p).append("&size=").append(s);
            if (keyword != null && !keyword.isBlank()) {
                url.append("&keyword=").append(URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8));
            }
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url.toString(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return PageResponse.of(List.of(), p, s, 0);
            }
            List<Map<String, Object>> raw = (List<Map<String, Object>>) body.get("content");
            long total = body.get("totalElements") instanceof Number n ? n.longValue() : 0;
            if (raw == null) {
                return PageResponse.of(List.of(), p, s, total);
            }
            List<Map<String, Object>> slim = new ArrayList<>();
            for (Map<String, Object> row : raw) {
                if (row == null) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("userId", Objects.toString(row.get("id"), null));
                m.put("username", Objects.toString(row.get("username"), null));
                m.put("displayName", firstNonBlank(
                        Objects.toString(row.get("fullName"), null),
                        Objects.toString(row.get("displayName"), null)));
                m.put("email", Objects.toString(row.get("email"), null));
                slim.add(m);
            }
            return PageResponse.of(slim, p, s, total);
        } catch (Exception e) {
            log.error("searchActiveUsersForPortal failed: {}", e.getMessage());
            return PageResponse.of(List.of(), p, s, 0);
        }
    }

    /**
     * 受益人须为可登录的 ACTIVE 用户
     */
    public boolean isActivePortalUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        Map<String, Object> u = getUserById(userId.trim());
        if (u == null || u.isEmpty()) {
            return false;
        }
        Object st = u.get("status");
        return st == null || "ACTIVE".equalsIgnoreCase(String.valueOf(st).trim());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
