package com.developer.security;

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
 * 通过 admin-center API 获取权限
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeveloperPermissionChecker {

    private final RestTemplate restTemplate;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    private final ConcurrentHashMap<String, CachedPermissions> permissionCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = TimeUnit.MINUTES.toMillis(5);

    /**
     * 获取用户的开发者权限
     */
    public Set<String> getUserPermissions(String userId) {
        log.info("Getting permissions for user: {}", userId);
        CachedPermissions cached = permissionCache.get(userId);
        if (cached != null && !cached.isExpired()) {
            log.info("Returning cached permissions for user {}: {} items", userId, cached.permissions.size());
            return cached.permissions;
        }

        log.info("Loading fresh permissions for user {}", userId);
        Set<String> permissions = loadFromAdminCenter(userId);
        if (permissions.isEmpty()) {
            log.error("Admin-center returned empty permissions for user {}", userId);
        }

        permissionCache.put(userId, new CachedPermissions(permissions));
        log.info("Cached {} permissions for user {}", permissions.size(), userId);
        return permissions;
    }

    private Set<String> loadFromAdminCenter(String userId) {
        try {
            String base = (adminCenterUrl != null && adminCenterUrl.endsWith("/api/v1/admin")) ? adminCenterUrl : adminCenterUrl + "/api/v1/admin";
            String url = base + "/developer-permissions/user/" + userId;
            log.info("Loading permissions from admin-center: {}", url);
            ResponseEntity<List<String>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<String>>() {}
            );
            List<String> permissions = response.getBody() != null ? response.getBody() : Collections.emptyList();
            log.info("Admin-center returned {} permissions for user {}: {}", permissions.size(), userId, permissions);
            return new HashSet<>(permissions);
        } catch (Exception e) {
            log.error("Admin-center permission request failed for user {}: {}", userId, e.getMessage(), e);
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
