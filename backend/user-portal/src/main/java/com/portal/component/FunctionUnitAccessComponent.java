package com.portal.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.platform.common.util.ApiResponseBodyUnwrap;

import java.util.*;
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
    
    private static final int MAX_CACHE_SIZE = 500;

    private final Map<String, CachedData<Set<String>>> userRolesCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedData<Set<String>>> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });
    
    private final Map<String, CachedData<Set<String>>> functionUnitAccessCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedData<Set<String>>> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });
    
    private final Map<String, CachedData<String>> processKeyCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedData<String>> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });
    
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
                Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response.getBody());
                Boolean enabled = (Boolean) payload.get("enabled");
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
     * 根据 ID、code、名称或流程定义Key获取功能单元的实际 ID
     */
    public String resolveFunctionUnitId(String functionUnitIdOrCode) {
        log.info("Resolving function unit ID for: {}", functionUnitIdOrCode);
        
        // 如果看起来像 UUID，先验证是否为有效的功能单元 ID
        // 注意：Flowable 7.0 的 processDefinitionId 也是 UUID 格式，不能直接当功能单元 ID 使用
        if (functionUnitIdOrCode != null && functionUnitIdOrCode.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            try {
                String verifyUrl = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitIdOrCode;
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        verifyUrl, HttpMethod.GET, null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );
                Map<String, Object> verifyPayload = response.getBody() != null
                        ? ApiResponseBodyUnwrap.unwrapDataMap(response.getBody()) : Collections.emptyMap();
                if (!verifyPayload.isEmpty() && verifyPayload.get("id") != null) {
                    log.info("UUID {} verified as valid function unit ID", functionUnitIdOrCode);
                    return functionUnitIdOrCode;
                }
            } catch (Exception e) {
                // UUID 不是有效的功能单元 ID（可能是 Flowable processDefinitionId），继续尝试其他查找方式
                log.warn("UUID {} is not a valid function unit ID (possibly a Flowable processDefinitionId), trying other lookup methods: {}", functionUnitIdOrCode, e.getMessage());
            }
            // 不能通过 code/processKey 查找 UUID，直接返回（作为最终兜底）
            log.warn("Could not resolve UUID {} to a function unit, returning as-is", functionUnitIdOrCode);
            return functionUnitIdOrCode;
        }
        
        // 检查 process key 缓存
        CachedData<String> cachedResult = processKeyCache.get(functionUnitIdOrCode);
        if (cachedResult != null && !cachedResult.isExpired()) {
            log.info("Returning cached function unit ID for process key {}: {}", functionUnitIdOrCode, cachedResult.data);
            return cachedResult.data;
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
                    Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response.getBody());
                    String id = (String) payload.get("id");
                    log.info("Resolved function unit code {} to ID {}", functionUnitIdOrCode, id);
                    return id;
                }
            } catch (Exception e) {
                log.warn("Failed to find function unit by code {}, trying by process key: {}", functionUnitIdOrCode, e.getMessage());
            }
            
            // 如果通过 code 找不到，尝试通过流程定义Key查找
            String processKeyUrl = adminCenterUrl + "/api/v1/admin/function-units/by-process-key/" + encodedParam;
            log.info("Fetching function unit by process key from: {}", processKeyUrl);
            
            try {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        processKeyUrl,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );
                
                if (response.getBody() != null) {
                    Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response.getBody());
                    String id = (String) payload.get("id");
                    log.info("Resolved function unit by process key {} to ID {}", functionUnitIdOrCode, id);
                    // 缓存 process key → function unit ID 映射
                    processKeyCache.put(functionUnitIdOrCode, new CachedData<>(id));
                    return id;
                }
            } catch (Exception e) {
                log.warn("Failed to find function unit by process key {}, trying by name: {}", functionUnitIdOrCode, e.getMessage());
            }
            
            // 如果通过流程Key找不到，尝试通过名称搜索
            String searchUrl = adminCenterUrl + "/api/v1/admin/function-units?keyword=" + encodedParam + "&size=1";
            log.info("Searching function unit by name from: {}", searchUrl);
            
            ResponseEntity<Map<String, Object>> searchResponse = restTemplate.exchange(
                    searchUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (searchResponse.getBody() != null) {
                java.util.List<Map<String, Object>> content = ApiResponseBodyUnwrap.normalizeToListOfMaps(searchResponse.getBody());
                if (!content.isEmpty()) {
                    // 只返回精确匹配名称的功能单元，避免模糊匹配返回错误结果
                    for (Map<String, Object> unit : content) {
                        String name = (String) unit.get("name");
                        if (functionUnitIdOrCode.equals(name)) {
                            String id = (String) unit.get("id");
                            log.info("Resolved function unit name {} to ID {}", functionUnitIdOrCode, id);
                            return id;
                        }
                    }
                    // 没有精确匹配，不使用模糊结果（避免加载错误的功能单元）
                    log.warn("No exact name match found for: {}, skipping fuzzy result to avoid wrong function unit", functionUnitIdOrCode);
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
            throw new FunctionUnitDisabledException("Function unit is disabled");
        }
        
        // 然后检查用户权限
        if (!canAccessFunctionUnit(userId, functionUnitId)) {
            log.warn("User {} does not have access to function unit {}", userId, functionUnitId);
            throw new FunctionUnitAccessDeniedException("You do not have permission to access this function unit");
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
     * 拉取管理中心已部署功能单元最新版本列表（与流程发起列表同源），供权限移除等场景按功能单元聚合展示。
     */
    public List<Map<String, Object>> fetchLatestDeployedFunctionUnits() {
        try {
            String url = adminCenterUrl + "/api/v1/admin/function-units/deployed/latest";
            log.debug("Fetching deployed function units from: {}", url);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null) {
                List<Map<String, Object>> units = ApiResponseBodyUnwrap.normalizeToListOfMaps(response);
                return units != null ? units : Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to fetch deployed function units: {}", e.getMessage());
        }
        return Collections.emptyList();
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
                    // 检查targetType是否为ROLE
                    String targetType = (String) access.get("targetType");
                    if ("ROLE".equals(targetType)) {
                        String roleId = (String) access.get("targetId");
                        if (roleId != null) {
                            log.info("Function unit {} allows role: {}", functionUnitId, roleId);
                            roleIds.add(roleId);
                        }
                    }
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
     * 清除 process key 缓存
     */
    public void clearProcessKeyCache(String processKey) {
        processKeyCache.remove(processKey);
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        userRolesCache.clear();
        functionUnitAccessCache.clear();
        processKeyCache.clear();
    }
    
    /**
     * 获取 process key 缓存大小（用于测试）
     */
    public int getProcessKeyCacheSize() {
        return processKeyCache.size();
    }
    
    /**
     * 检查 process key 是否在缓存中（用于测试）
     */
    public boolean isProcessKeyCached(String processKey) {
        CachedData<String> cached = processKeyCache.get(processKey);
        return cached != null && !cached.isExpired();
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
