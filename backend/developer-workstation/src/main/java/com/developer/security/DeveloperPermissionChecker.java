package com.developer.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 开发者权限检查器
 * 通过调用 admin-center API 获取用户的开发者权限
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeveloperPermissionChecker {
    
    private final RestTemplate restTemplate;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;
    
    // 简单的权限缓存，避免频繁调用 admin-center
    private final ConcurrentHashMap<String, CachedPermissions> permissionCache = new ConcurrentHashMap<>();
    
    // 缓存过期时间（毫秒）
    private static final long CACHE_TTL = TimeUnit.MINUTES.toMillis(5);
    
    /**
     * 获取用户的开发者权限
     */
    public Set<String> getUserPermissions(String userId) {
        // 检查缓存
        CachedPermissions cached = permissionCache.get(userId);
        if (cached != null && !cached.isExpired()) {
            return cached.permissions;
        }
        
        try {
            // admin-center 的 context-path 是 /api/v1/admin
            String url = adminCenterUrl + "/api/v1/admin/developer-permissions/user/" + userId;
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            
            Set<String> permissions = new HashSet<>(response.getBody() != null ? response.getBody() : Collections.emptyList());
            
            // 更新缓存
            permissionCache.put(userId, new CachedPermissions(permissions));
            
            log.debug("Loaded permissions for user {}: {}", userId, permissions);
            return permissions;
            
        } catch (Exception e) {
            log.error("Failed to load permissions for user {}: {}", userId, e.getMessage());
            // 如果有过期的缓存，返回过期的数据
            if (cached != null) {
                return cached.permissions;
            }
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
