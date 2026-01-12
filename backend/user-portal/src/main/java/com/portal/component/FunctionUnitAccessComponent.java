package com.portal.component;

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
 * 功能单元访问权限组件
 * 根据用户的业务角色过滤可访问的功能单元
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitAccessComponent {
    
    private final RestTemplate restTemplate;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;
    
    // 缓存用户的业务角色ID
    private final ConcurrentHashMap<String, CachedData<Set<String>>> userRolesCache = new ConcurrentHashMap<>();
    
    // 缓存功能单元的访问配置
    private final ConcurrentHashMap<String, CachedData<Set<String>>> functionUnitAccessCache = new ConcurrentHashMap<>();
    
    private static final long CACHE_TTL = TimeUnit.MINUTES.toMillis(5);
    
    /**
     * 检查用户是否可以访问指定的功能单元
     */
    public boolean canAccessFunctionUnit(String userId, String functionUnitId) {
        // 获取功能单元的访问配置（允许访问的角色ID列表）
        Set<String> allowedRoleIds = getFunctionUnitAllowedRoles(functionUnitId);
        
        // 如果没有配置访问权限，则所有用户都可以访问
        if (allowedRoleIds.isEmpty()) {
            return true;
        }
        
        // 获取用户的业务角色ID列表
        Set<String> userRoleIds = getUserBusinessRoleIds(userId);
        
        // 检查是否有交集
        for (String roleId : userRoleIds) {
            if (allowedRoleIds.contains(roleId)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查功能单元是否启用
     * @return true 如果启用，false 如果禁用或无法获取状态
     */
    public boolean isFunctionUnitEnabled(String functionUnitIdOrCode) {
        log.info("Checking if function unit {} is enabled", functionUnitIdOrCode);
        
        try {
            // 先尝试通过 ID 获取
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitIdOrCode;
            log.info("Fetching function unit info from: {}", url);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getBody() != null) {
                Boolean enabled = (Boolean) response.getBody().get("enabled");
                log.info("Function unit {} enabled status: {}", functionUnitIdOrCode, enabled);
                // 默认为 true（如果字段不存在）
                return enabled == null || enabled;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to check function unit enabled status for {}: {}", functionUnitIdOrCode, e.getMessage(), e);
            // 出错时默认允许访问，避免阻断用户
            return true;
        }
    }
    
    /**
     * 根据 ID、code 或名称获取功能单元的实际 ID
     */
    public String resolveFunctionUnitId(String functionUnitIdOrCode) {
        log.info("Resolving function unit ID for: {}", functionUnitIdOrCode);
        
        // 如果看起来像 UUID，直接返回
        if (functionUnitIdOrCode != null && functionUnitIdOrCode.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return functionUnitIdOrCode;
        }
        
        try {
            // 对参数进行 URL 编码（支持中文）
            String encodedParam = java.net.URLEncoder.encode(functionUnitIdOrCode, java.nio.charset.StandardCharsets.UTF_8);
            
            // 首先尝试通过 code 查找功能单元
            String url = adminCenterUrl + "/api/v1/admin/function-units/code/" + encodedParam + "/latest";
            log.info("Fetching function unit by code from: {}", url);
            
            try {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );
                
                if (response.getBody() != null) {
                    String id = (String) response.getBody().get("id");
                    log.info("Resolved function unit code {} to ID {}", functionUnitIdOrCode, id);
                    return id;
                }
            } catch (Exception e) {
                log.warn("Failed to find function unit by code {}, trying by name: {}", functionUnitIdOrCode, e.getMessage());
            }
            
            // 如果通过 code 找不到，尝试通过名称搜索
            String searchUrl = adminCenterUrl + "/api/v1/admin/function-units?keyword=" + encodedParam + "&size=1";
            log.info("Searching function unit by name from: {}", searchUrl);
            
            ResponseEntity<Map<String, Object>> searchResponse = restTemplate.exchange(
                    searchUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (searchResponse.getBody() != null) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> content = (java.util.List<Map<String, Object>>) searchResponse.getBody().get("content");
                if (content != null && !content.isEmpty()) {
                    // 找到精确匹配名称的功能单元
                    for (Map<String, Object> unit : content) {
                        String name = (String) unit.get("name");
                        if (functionUnitIdOrCode.equals(name)) {
                            String id = (String) unit.get("id");
                            log.info("Resolved function unit name {} to ID {}", functionUnitIdOrCode, id);
                            return id;
                        }
                    }
                    // 如果没有精确匹配，返回第一个结果
                    String id = (String) content.get(0).get("id");
                    log.info("Resolved function unit (first match) {} to ID {}", functionUnitIdOrCode, id);
                    return id;
                }
            }
            
            log.warn("Could not resolve function unit ID for: {}", functionUnitIdOrCode);
            return functionUnitIdOrCode;
            
        } catch (Exception e) {
            log.error("Failed to resolve function unit ID for {}: {}", functionUnitIdOrCode, e.getMessage(), e);
            return functionUnitIdOrCode;
        }
    }
    
    /**
     * 检查用户是否可以访问指定的功能单元（包含启用状态检查）
     * @throws FunctionUnitDisabledException 如果功能单元已禁用
     */
    public void checkFunctionUnitAccess(String userId, String functionUnitIdOrCode) {
        // 先解析功能单元 ID
        String functionUnitId = resolveFunctionUnitId(functionUnitIdOrCode);
        
        // 首先检查功能单元是否启用
        if (!isFunctionUnitEnabled(functionUnitId)) {
            log.warn("Function unit {} is disabled, access denied for user {}", functionUnitId, userId);
            throw new FunctionUnitDisabledException("功能单元已禁用");
        }
        
        // 然后检查用户权限
        if (!canAccessFunctionUnit(userId, functionUnitId)) {
            log.warn("User {} does not have access to function unit {}", userId, functionUnitId);
            throw new FunctionUnitAccessDeniedException("您没有访问此功能单元的权限");
        }
    }
    
    /**
     * 功能单元已禁用异常
     */
    public static class FunctionUnitDisabledException extends RuntimeException {
        public FunctionUnitDisabledException(String message) {
            super(message);
        }
    }
    
    /**
     * 功能单元访问被拒绝异常
     */
    public static class FunctionUnitAccessDeniedException extends RuntimeException {
        public FunctionUnitAccessDeniedException(String message) {
            super(message);
        }
    }
    
    /**
     * 过滤用户可访问的功能单元列表
     * 过滤条件：1. 功能单元已启用 2. 用户有访问权限
     */
    public List<Map<String, Object>> filterAccessibleFunctionUnits(String userId, List<Map<String, Object>> functionUnits) {
        if (functionUnits == null || functionUnits.isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> userRoleIds = getUserBusinessRoleIds(userId);
        List<Map<String, Object>> accessible = new ArrayList<>();
        
        for (Map<String, Object> unit : functionUnits) {
            String unitId = (String) unit.get("id");
            
            // 检查功能单元是否启用
            Boolean enabled = (Boolean) unit.get("enabled");
            if (enabled != null && !enabled) {
                log.debug("Function unit {} is disabled, skipping", unitId);
                continue;
            }
            
            Set<String> allowedRoleIds = getFunctionUnitAllowedRoles(unitId);
            
            // 如果没有配置访问权限，或者用户有允许的角色
            if (allowedRoleIds.isEmpty() || hasAnyRole(userRoleIds, allowedRoleIds)) {
                accessible.add(unit);
            }
        }
        
        return accessible;
    }
    
    /**
     * 获取用户的业务角色ID列表
     */
    public Set<String> getUserBusinessRoleIds(String userId) {
        log.info("Getting business roles for user: {}", userId);
        
        CachedData<Set<String>> cached = userRolesCache.get(userId);
        if (cached != null && !cached.isExpired()) {
            log.info("Returning cached roles for user {}: {}", userId, cached.data);
            return cached.data;
        }
        
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + userId + "/roles?type=BUSINESS";
            log.info("Fetching user roles from: {}", url);
            
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            Set<String> roleIds = new HashSet<>();
            if (response.getBody() != null) {
                log.info("Got {} roles for user {}", response.getBody().size(), userId);
                for (Map<String, Object> role : response.getBody()) {
                    String roleId = (String) role.get("id");
                    log.info("User {} has role: {}", userId, roleId);
                    roleIds.add(roleId);
                }
            }
            
            userRolesCache.put(userId, new CachedData<>(roleIds));
            return roleIds;
            
        } catch (Exception e) {
            log.error("Failed to get user business roles for user {}: {}", userId, e.getMessage(), e);
            if (cached != null) {
                return cached.data;
            }
            return Collections.emptySet();
        }
    }
    
    /**
     * 获取功能单元允许访问的角色ID列表
     */
    public Set<String> getFunctionUnitAllowedRoles(String functionUnitId) {
        log.info("Getting allowed roles for function unit: {}", functionUnitId);
        
        CachedData<Set<String>> cached = functionUnitAccessCache.get(functionUnitId);
        if (cached != null && !cached.isExpired()) {
            log.info("Returning cached allowed roles for function unit {}: {}", functionUnitId, cached.data);
            return cached.data;
        }
        
        try {
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/access";
            log.info("Fetching function unit access from: {}", url);
            
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            Set<String> roleIds = new HashSet<>();
            if (response.getBody() != null) {
                log.info("Got {} access records for function unit {}", response.getBody().size(), functionUnitId);
                for (Map<String, Object> access : response.getBody()) {
                    String roleId = (String) access.get("roleId");
                    log.info("Function unit {} allows role: {}", functionUnitId, roleId);
                    roleIds.add(roleId);
                }
            }
            
            functionUnitAccessCache.put(functionUnitId, new CachedData<>(roleIds));
            return roleIds;
            
        } catch (Exception e) {
            log.error("Failed to get function unit access config for {}: {}", functionUnitId, e.getMessage(), e);
            if (cached != null) {
                return cached.data;
            }
            return Collections.emptySet();
        }
    }
    
    /**
     * 清除用户角色缓存
     */
    public void clearUserRolesCache(String userId) {
        userRolesCache.remove(userId);
    }
    
    /**
     * 清除功能单元访问缓存
     */
    public void clearFunctionUnitAccessCache(String functionUnitId) {
        functionUnitAccessCache.remove(functionUnitId);
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        userRolesCache.clear();
        functionUnitAccessCache.clear();
    }
    
    private boolean hasAnyRole(Set<String> userRoleIds, Set<String> allowedRoleIds) {
        for (String roleId : userRoleIds) {
            if (allowedRoleIds.contains(roleId)) {
                return true;
            }
        }
        return false;
    }
    
    private static class CachedData<T> {
        final T data;
        final long timestamp;
        
        CachedData(T data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL;
        }
    }
}
