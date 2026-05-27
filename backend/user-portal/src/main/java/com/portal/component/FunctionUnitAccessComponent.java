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
 * Function unit access permission component
 * Filters accessible function units based on the user's business roles
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitAccessComponent {
    
    private final RestTemplate restTemplate;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;
    
    private static final int MAX_CACHE_SIZE = 500;

    /** Lowercase UUID shape: used for function-unit id vs Flowable id heuristics (same as historical resolve logic). */
    private static final String LOWERCASE_UUID_REGEX =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

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
     * Check if a user can access a specified function unit
     */
    public boolean canAccessFunctionUnit(String userId, String functionUnitId) {
        // Get the function unit's access configuration (list of allowed role IDs)
        Set<String> allowedRoleIds = getFunctionUnitAllowedRoles(functionUnitId);
        
        // If no access permissions are configured, all users can access
        if (allowedRoleIds.isEmpty()) {
            return true;
        }
        
        // Get the user's business role ID list
        Set<String> userRoleIds = getUserBusinessRoleIds(userId);
        
        // Check if there is any intersection
        for (String roleId : userRoleIds) {
            if (allowedRoleIds.contains(roleId)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a function unit is enabled
     * @return true if enabled, false if disabled or unable to determine status
     */
    public boolean isFunctionUnitEnabled(String functionUnitIdOrCode) {
        log.info("Checking if function unit {} is enabled", functionUnitIdOrCode);
        
        try {
            // Try fetching by ID first
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
                Boolean enabled = parseEnabledFlag(payload.get("enabled"));
                log.info("Function unit {} enabled status: {}", functionUnitIdOrCode, enabled);
                // Default to true (if field does not exist)
                return enabled == null || enabled;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to check function unit enabled status for {}: {}", functionUnitIdOrCode, e.getMessage(), e);
            // Default to allowing access on error to avoid blocking the user
            return true;
        }
    }
    
    /**
     * Resolve the actual function unit ID by ID, code, name, or process definition key
     */
    public String resolveFunctionUnitId(String functionUnitIdOrCode) {
        log.info("Resolving function unit ID for: {}", functionUnitIdOrCode);
        
        // If it looks like a UUID, first verify whether it's a valid function unit ID
        // Note: Flowable 7.0 processDefinitionId is also in UUID format and cannot be directly used as a function unit ID
        if (functionUnitIdOrCode != null && functionUnitIdOrCode.matches(LOWERCASE_UUID_REGEX)) {
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
                // UUID is not a valid function unit ID (may be a Flowable processDefinitionId), continue trying other lookup methods
                log.warn("UUID {} is not a valid function unit ID (possibly a Flowable processDefinitionId), trying other lookup methods: {}", functionUnitIdOrCode, e.getMessage());
            }
            // Cannot look up a UUID via code/processKey, return as-is (final fallback)
            log.warn("Could not resolve UUID {} to a function unit, returning as-is", functionUnitIdOrCode);
            return functionUnitIdOrCode;
        }
        
        // Check process key cache
        CachedData<String> cachedResult = processKeyCache.get(functionUnitIdOrCode);
        if (cachedResult != null && !cachedResult.isExpired()) {
            log.info("Returning cached function unit ID for process key {}: {}", functionUnitIdOrCode, cachedResult.data);
            return cachedResult.data;
        }
        
        try {
            // URL-encode the parameter (supports Chinese characters)
            String encodedParam = java.net.URLEncoder.encode(functionUnitIdOrCode, java.nio.charset.StandardCharsets.UTF_8);

            // Tasks/process instances typically pass a Flowable processDefinitionKey, so resolve by process key first:
            // admin-center's getFunctionUnitByProcessKey will preferentially return the still-enabled catalog entry.
            // If resolved by code/latest first, it could match a different package's latest version with the same name that is disabled, falsely reporting "Function unit is disabled".
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
                    processKeyCache.put(functionUnitIdOrCode, new CachedData<>(id));
                    return id;
                }
            } catch (Exception e) {
                log.warn("Failed to find function unit by process key {}, trying by code: {}", functionUnitIdOrCode, e.getMessage());
            }

            // Portal-initiated lists and similar pass a catalog code; when process key misses, look up the latest version by code
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
                log.warn("Failed to find function unit by code {}, trying by name: {}", functionUnitIdOrCode, e.getMessage());
            }
            
            // When neither process key nor code hits, search by exact name match
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
                    // Only return function units with exact name match, avoid fuzzy matching wrong results
                    for (Map<String, Object> unit : content) {
                        String name = (String) unit.get("name");
                        if (functionUnitIdOrCode.equals(name)) {
                            String id = (String) unit.get("id");
                            log.info("Resolved function unit name {} to ID {}", functionUnitIdOrCode, id);
                            return id;
                        }
                    }
                    // No exact match, skip fuzzy results (to avoid loading the wrong function unit)
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
     * Check if a user can access the specified function unit (includes enabled status check)
     * @throws FunctionUnitDisabledException if the function unit is disabled
     */
    public void checkFunctionUnitAccess(String userId, String functionUnitIdOrCode) {
        // First resolve the function unit ID
        String functionUnitId = resolveFunctionUnitId(functionUnitIdOrCode);

        // First check if the function unit is enabled
        if (!isFunctionUnitEnabled(functionUnitId)) {
            // Resolved results for processDefinitionKey / catalog code are cached in processKeyCache (TTL 5min).
            // After an admin disables an old catalog entry and enables a new version, the cache may still point to the disabled ID, causing false "disabled" errors in task views.
            if (mayResolveViaProcessKeyCache(functionUnitIdOrCode)) {
                log.info(
                        "Resolved function unit {} appears disabled; invalidating process-key cache for lookup key [{}] and re-resolving once",
                        functionUnitId,
                        functionUnitIdOrCode);
                clearProcessKeyCache(functionUnitIdOrCode);
                functionUnitId = resolveFunctionUnitId(functionUnitIdOrCode);
            }
        }
        if (!isFunctionUnitEnabled(functionUnitId)) {
            log.warn("Function unit {} is disabled, access denied for user {}", functionUnitId, userId);
            throw new FunctionUnitDisabledException("Function unit is disabled");
        }
        
        // Then check user permissions
        if (!canAccessFunctionUnit(userId, functionUnitId)) {
            log.warn("User {} does not have access to function unit {}", userId, functionUnitId);
            throw new FunctionUnitAccessDeniedException("You do not have permission to access this function unit");
        }
    }
    
    /**
     * Function unit disabled exception
     */
    public static class FunctionUnitDisabledException extends RuntimeException {
        public FunctionUnitDisabledException(String message) {
            super(message);
        }
    }
    
    /**
     * Function unit access denied exception
     */
    public static class FunctionUnitAccessDeniedException extends RuntimeException {
        public FunctionUnitAccessDeniedException(String message) {
            super(message);
        }
    }
    
    /**
     * Filter the list of function units accessible to a user
     * Filter conditions: 1. Function unit is enabled 2. User has access permission
     */
    public List<Map<String, Object>> filterAccessibleFunctionUnits(String userId, List<Map<String, Object>> functionUnits) {
        if (functionUnits == null || functionUnits.isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> userRoleIds = getUserBusinessRoleIds(userId);
        List<Map<String, Object>> accessible = new ArrayList<>();
        
        for (Map<String, Object> unit : functionUnits) {
            String unitId = (String) unit.get("id");
            
            // Check if the function unit is enabled
            Boolean enabled = parseEnabledFlag(unit.get("enabled"));
            if (enabled != null && !enabled) {
                log.debug("Function unit {} is disabled, skipping", unitId);
                continue;
            }
            
            Set<String> allowedRoleIds = getFunctionUnitAllowedRoles(unitId);
            
            // If no access permissions are configured, or the user has an allowed role
            if (allowedRoleIds.isEmpty() || hasAnyRole(userRoleIds, allowedRoleIds)) {
                accessible.add(unit);
            }
        }
        
        return accessible;
    }

    /**
     * Fetch the latest version list of deployed function units from the admin center (same source as the process initiation list),
     * for use in scenarios like permission removal where function-unit-level aggregation display is needed.
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
     * Get the user's business role ID list
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
     * Get the list of role IDs allowed to access a function unit
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
                    // Check if targetType is ROLE
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
     * Clear user role cache
     */
    public void clearUserRolesCache(String userId) {
        userRolesCache.remove(userId);
    }
    
    /**
     * Clear function unit access cache
     */
    public void clearFunctionUnitAccessCache(String functionUnitId) {
        functionUnitAccessCache.remove(functionUnitId);
    }
    
    /**
     * Clear process key cache
     */
    public void clearProcessKeyCache(String processKey) {
        processKeyCache.remove(processKey);
    }
    
    /**
     * Clear all caches
     */
    public void clearAllCache() {
        userRolesCache.clear();
        functionUnitAccessCache.clear();
        processKeyCache.clear();
    }
    
    /**
     * Get process key cache size (for testing)
     */
    public int getProcessKeyCacheSize() {
        return processKeyCache.size();
    }
    
    /**
     * Check if a process key is in the cache (for testing)
     */
    public boolean isProcessKeyCached(String processKey) {
        CachedData<String> cached = processKeyCache.get(processKey);
        return cached != null && !cached.isExpired();
    }
    
    /**
     * Consistent with {@link #resolveFunctionUnitId}: UUID-shaped parameters go through the "verify by ID" path
     * and do not use the processKey cache key.
     */
    private boolean mayResolveViaProcessKeyCache(String functionUnitIdOrCode) {
        if (functionUnitIdOrCode == null || functionUnitIdOrCode.isBlank()) {
            return false;
        }
        return !functionUnitIdOrCode.matches(LOWERCASE_UUID_REGEX);
    }

    private boolean hasAnyRole(Set<String> userRoleIds, Set<String> allowedRoleIds) {
        for (String roleId : userRoleIds) {
            if (allowedRoleIds.contains(roleId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Parse the enabled field returned by admin-center (compatible with Boolean / Number / String),
     * unrecognized types are treated as null (i.e., not disabled).
     */
    private static Boolean parseEnabledFlag(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return null;
            }
            if ("1".equals(t) || "true".equalsIgnoreCase(t)) {
                return true;
            }
            if ("0".equals(t) || "false".equalsIgnoreCase(t)) {
                return false;
            }
        }
        return null;
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
