package com.developer.security;

import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 开发者权限检查器
 * 优先通过 admin-center API 获取权限；若不可用或返回空，则用本地 DB 角色做回退（与登录逻辑一致）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeveloperPermissionChecker {

    private final RestTemplate restTemplate;
    private final UserRoleService userRoleService;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    private final ConcurrentHashMap<String, CachedPermissions> permissionCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = TimeUnit.MINUTES.toMillis(5);

    /** 本地回退：按角色代码映射的开发者权限（与 admin-center DeveloperPermissionService 一致） */
    private static final Map<String, Set<String>> FALLBACK_ROLE_PERMISSIONS = new HashMap<>();
    static {
        Set<String> all = Set.of(
            "function_unit:create", "function_unit:update", "function_unit:delete", "function_unit:view",
            "function_unit:develop", "function_unit:publish",
            "form:create", "form:update", "form:delete", "form:view",
            "process:create", "process:update", "process:delete", "process:view",
            "table:create", "table:update", "table:delete", "table:view",
            "action:create", "action:update", "action:delete", "action:view"
        );
        FALLBACK_ROLE_PERMISSIONS.put("TECH_DIRECTOR", all);
        FALLBACK_ROLE_PERMISSIONS.put("DEV_LEAD", all);
        FALLBACK_ROLE_PERMISSIONS.put("TEAM_LEADER", all);
        FALLBACK_ROLE_PERMISSIONS.put("SENIOR_DEV", all);
        FALLBACK_ROLE_PERMISSIONS.put("DEVELOPER", all);
    }

    /**
     * 获取用户的开发者权限
     */
    public Set<String> getUserPermissions(String userId) {
        CachedPermissions cached = permissionCache.get(userId);
        if (cached != null && !cached.isExpired()) {
            return cached.permissions;
        }

        Set<String> permissions = loadFromAdminCenter(userId);
        if (permissions.isEmpty()) {
            permissions = loadFromLocalRoles(userId);
            if (!permissions.isEmpty()) {
                log.info("Developer permissions for user {} loaded from local roles (fallback): {} items", userId, permissions.size());
            }
        }

        permissionCache.put(userId, new CachedPermissions(permissions));
        return permissions;
    }

    private Set<String> loadFromAdminCenter(String userId) {
        try {
            String url = adminCenterUrl + "/api/v1/admin/developer-permissions/user/" + userId;
            ResponseEntity<List<String>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<String>>() {}
            );
            return new HashSet<>(response.getBody() != null ? response.getBody() : Collections.emptyList());
        } catch (Exception e) {
            log.warn("Admin-center permission request failed for user {}: {}", userId, e.getMessage());
            return Collections.emptySet();
        }
    }

    private Set<String> loadFromLocalRoles(String userId) {
        try {
            List<UserEffectiveRole> roles = userRoleService.getEffectiveRolesForUser(userId);
            Set<String> permissions = new HashSet<>();
            for (UserEffectiveRole r : roles) {
                String code = r.getRoleCode();
                if (code != null && FALLBACK_ROLE_PERMISSIONS.containsKey(code)) {
                    permissions.addAll(FALLBACK_ROLE_PERMISSIONS.get(code));
                }
            }
            return permissions;
        } catch (Exception e) {
            log.warn("Local role fallback failed for user {}: {}", userId, e.getMessage());
            return Collections.emptySet();
        }
    }
    
    /**
     * 检查用户是否有指定权限
     */
    public boolean hasPermission(String userId, String permission) {
        return getUserPermissions(userId).contains(permission);
    }
    
    /**
     * 检查用户是否有任一指定权限
     */
    public boolean hasAnyPermission(String userId, String... permissions) {
        Set<String> userPermissions = getUserPermissions(userId);
        for (String permission : permissions) {
            if (userPermissions.contains(permission)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查用户是否有所有指定权限
     */
    public boolean hasAllPermissions(String userId, String... permissions) {
        Set<String> userPermissions = getUserPermissions(userId);
        for (String permission : permissions) {
            if (!userPermissions.contains(permission)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 清除用户权限缓存
     */
    public void clearCache(String userId) {
        permissionCache.remove(userId);
    }
    
    /**
     * 清除所有权限缓存
     */
    public void clearAllCache() {
        permissionCache.clear();
    }
    
    /**
     * 缓存的权限数据
     */
    private static class CachedPermissions {
        final Set<String> permissions;
        final long timestamp;
        
        CachedPermissions(Set<String> permissions) {
            this.permissions = permissions;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL;
        }
    }
}
