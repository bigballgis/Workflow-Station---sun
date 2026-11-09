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
import com.platform.common.util.SafeUrlInput;
import com.portal.util.PortalUserSecurityUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Function unit access permission component.
 * Launch, view and audit access follow the user's <em>current</em> workspace role only, like every
 * other Portal business check (platform access model §7.2); no role bypasses the access
 * configuration, System Administrator included (D-05).
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

    /**
     * The user's portal roles as admin-center lists them (id + code), keyed by user. Workspace
     * independent: the current role is picked out after reading, so a workspace switch applies at once.
     */
    private final Map<String, CachedData<List<Map<String, Object>>>> userRolePayloadsCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedData<List<Map<String, Object>>>> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });

    /** Code-keyed cache for New-Request visibility filtering (role code matching, stable across environments). */
    private final Map<String, CachedData<Set<String>>> userRoleCodesCache = Collections.synchronizedMap(
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
    /** A cached role list missing the session's current role is read again once it is this old. */
    private static final long ROLE_MISS_REFETCH_AFTER_MS = TimeUnit.SECONDS.toMillis(10);

    /**
     * Short TTL for "could not resolve" results. Keeps repeated list/detail requests from re-running
     * the same failing admin-center lookups, while still picking up a newly deployed function unit quickly.
     */
    private static final long UNRESOLVED_CACHE_TTL = TimeUnit.SECONDS.toMillis(30);

    /**
     * Must include {@code profileContext=PORTAL} so admin-center merges UBR roles
     * ({@code sys_user_business_unit_roles}), not only virtual-group inheritance.
     */
    private static final String USER_BUSINESS_ROLES_QUERY = "profileContext=PORTAL";

    /**
     * Check if a user can access a specified function unit: its access roles must include the user's
     * current workspace role (same keys as the New Request catalog, {@link #resolveNewRequestRoleKeys}).
     */
    public boolean canAccessFunctionUnit(String userId, String functionUnitIdOrCode) {
        String resolvedId = resolveFunctionUnitId(functionUnitIdOrCode);
        // Access rows live on deployed catalog IDs; dw codes / process keys must be resolved first.
        Set<String> allowedRoleCodes = getFunctionUnitAllowedRoleCodes(resolvedId);

        // No access config → deny (same as Relation Table access; Admin must assign roles explicitly)
        if (allowedRoleCodes.isEmpty()) {
            return false;
        }

        String activeRoleId = PortalUserSecurityUtils.getCurrentActiveRoleId().orElse(null);
        return hasAnyRole(resolveNewRequestRoleKeys(userId, activeRoleId), allowedRoleCodes);
    }
    
    /**
     * Check if a function unit is enabled.
     *
     * <p>The admin-center detail endpoint is keyed by the catalog UUID, so a dw code /
     * processDefinitionKey has to be resolved first — same order as {@link #canAccessFunctionUnit}
     * and the primary-key allocation path. Handing it a raw code could never match a UUID primary
     * key, so every lookup threw and fell back to "enabled", which disabled the gate outright.
     *
     * @return true only when admin-center confirms the unit is enabled; false when it is disabled,
     *         has no deployed catalog entry, or admin-center cannot answer (fail-closed)
     */
    public boolean isFunctionUnitEnabled(String functionUnitIdOrCode) {
        String catalogId = toCatalogId(functionUnitIdOrCode);
        if (catalogId == null) {
            log.warn("Function unit {} has no deployed catalog entry, treating as disabled", functionUnitIdOrCode);
            return false;
        }

        try {
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + SafeUrlInput.requirePathToken(catalogId);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> payload = response.getBody() != null
                    ? ApiResponseBodyUnwrap.unwrapDataMap(response.getBody())
                    : Collections.emptyMap();
            if (payload.isEmpty()) {
                log.warn("Empty function unit detail for {} (requested {}), treating as disabled",
                        catalogId, functionUnitIdOrCode);
                return false;
            }

            Boolean enabled = parseEnabledFlag(payload.get("enabled"));
            log.debug("Function unit {} (catalog {}) enabled status: {}", functionUnitIdOrCode, catalogId, enabled);
            // Absent flag means a catalog row that predates the column; treat those as enabled.
            return enabled == null || enabled;

        } catch (Exception e) {
            // Fail-closed: an unreachable admin-center must not silently grant access to a
            // function unit that may have been disabled.
            log.warn("Failed to check enabled status for {} (catalog {}), treating as disabled: {}",
                    functionUnitIdOrCode, catalogId, e.getMessage());
            return false;
        }
    }

    /**
     * Map an id / dw code / processDefinitionKey onto a deployed catalog UUID, or null when it does
     * not resolve. {@link #resolveFunctionUnitId} echoes its argument back when every lookup misses,
     * so a non-UUID result is the "unresolved" signal.
     */
    private String toCatalogId(String functionUnitIdOrCode) {
        if (functionUnitIdOrCode == null || functionUnitIdOrCode.isBlank()) {
            return null;
        }
        // Already a catalog id: resolving again would only re-verify it over HTTP.
        if (functionUnitIdOrCode.matches(LOWERCASE_UUID_REGEX)) {
            return functionUnitIdOrCode;
        }
        String resolved = resolveFunctionUnitId(functionUnitIdOrCode);
        return resolved != null && resolved.matches(LOWERCASE_UUID_REGEX) ? resolved : null;
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
                String verifyUrl = adminCenterUrl + "/api/v1/admin/function-units/" + SafeUrlInput.requirePathToken(functionUnitIdOrCode);
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
                    processKeyCache.put(functionUnitIdOrCode, new CachedData<>(id));
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
                            processKeyCache.put(functionUnitIdOrCode, new CachedData<>(id));
                            return id;
                        }
                    }
                    // No exact match, skip fuzzy results (to avoid loading the wrong function unit)
                    log.warn("No exact name match found for: {}, skipping fuzzy result to avoid wrong function unit", functionUnitIdOrCode);
                }
            }
            
            log.warn("Could not resolve function unit ID for: {}", functionUnitIdOrCode);
            // Negative result is cached (short TTL) so repeated list/detail requests do not re-run the
            // same 3 failing admin-center lookups on every call. Same return value as before.
            processKeyCache.put(functionUnitIdOrCode,
                    new CachedData<>(functionUnitIdOrCode, UNRESOLVED_CACHE_TTL));
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
        String functionUnitId = requireEnabledFunctionUnit(userId, functionUnitIdOrCode);

        // Then check user permissions
        if (!canAccessFunctionUnit(userId, functionUnitId)) {
            log.warn("User {} does not have access to function unit {}", userId, functionUnitId);
            throw new FunctionUnitAccessDeniedException("You do not have permission to access this function unit");
        }
    }

    /**
     * Resolve the function unit and verify it is enabled, without the role-based access check.
     * Used by task-participant content access, where assignees may lack the FU's start-access
     * roles but the disabled gate must still apply.
     *
     * @return the resolved function unit ID
     * @throws FunctionUnitDisabledException if the function unit is disabled
     */
    public String requireEnabledFunctionUnit(String userId, String functionUnitIdOrCode) {
        String functionUnitId = resolveFunctionUnitId(functionUnitIdOrCode);

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
        return functionUnitId;
    }

    /**
     * Aligns the process-key cache with Admin {@code active-for-start}. Import/redeploy
     * changes the enabled catalog id while this cache can still hold the previous id
     * for up to {@link #CACHE_TTL}; Email Monitor and Portal start must not wait for
     * TTL or a user-portal restart.
     */
    public String resolveFunctionUnitIdAlignedWithActiveCatalog(String processKey, String activeCatalogId) {
        String resolved = resolveFunctionUnitId(processKey);
        if (activeCatalogId != null && activeCatalogId.equals(resolved)) {
            return resolved;
        }
        if (!mayResolveViaProcessKeyCache(processKey)) {
            return resolved;
        }
        log.info(
                "Active catalog {} differs from resolved {}; invalidating process-key cache for [{}]",
                activeCatalogId, resolved, processKey);
        clearProcessKeyCache(processKey);
        resolved = resolveFunctionUnitId(processKey);
        if (activeCatalogId != null && !activeCatalogId.equals(resolved)) {
            // /latest is highest semver, including a newer DISABLED catalog. Email/portal start
            // already fetched Admin active-for-start (deployed+enabled); that pin wins.
            log.info("Using active-for-start catalog {} instead of latest-resolved {} for [{}]",
                    activeCatalogId, resolved, processKey);
            processKeyCache.put(processKey, new CachedData<>(activeCatalogId));
            return activeCatalogId;
        }
        return resolved;
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
     * New Request catalog: enabled FUs whose access roles intersect the current workspace role.
     * Without an {@code activeRoleId} only a permission self-service session uses all its portal roles; any other
     * session gets none (see {@link #resolveNewRequestRoleKeys}).
     */
    public List<Map<String, Object>> filterAccessibleFunctionUnits(String userId, List<Map<String, Object>> functionUnits) {
        String activeRoleId = PortalUserSecurityUtils.getCurrentActiveRoleId().orElse(null);
        return filterAccessibleFunctionUnits(userId, functionUnits, activeRoleId);
    }

    /**
     * @param activeRoleId JWT workspace role id; blank means "no workspace" (all portal roles in a permission
     *                     self-service session, none otherwise)
     */
    public List<Map<String, Object>> filterAccessibleFunctionUnits(
            String userId, List<Map<String, Object>> functionUnits, String activeRoleId) {
        if (functionUnits == null || functionUnits.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> userRoleKeys = resolveNewRequestRoleKeys(userId, activeRoleId);
        if (userRoleKeys.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> accessible = new ArrayList<>();
        for (Map<String, Object> unit : functionUnits) {
            if (isUnitVisibleForRoles(unit, userRoleKeys)) {
                accessible.add(unit);
            }
        }
        return accessible;
    }

    private boolean isUnitVisibleForRoles(Map<String, Object> unit, Set<String> userRoleKeys) {
        String unitId = (String) unit.get("id");
        Boolean enabled = parseEnabledFlag(unit.get("enabled"));
        if (enabled != null && !enabled) {
            log.debug("Function unit {} is disabled, skipping", unitId);
            return false;
        }
        Set<String> allowedRoleCodes = getFunctionUnitAllowedRoleCodes(unitId);
        return !allowedRoleCodes.isEmpty() && hasAnyRole(userRoleKeys, allowedRoleCodes);
    }

    /**
     * Workspace mode: only the selected role (id + code). A permission self-service session has no workspace and keeps
     * all its portal role codes (it only reaches its own requests); any other session without a current role gets
     * none — the user's roles are never combined across workspaces (platform access model §7.2).
     */
    Set<String> resolveNewRequestRoleKeys(String userId, String activeRoleId) {
        if (activeRoleId == null || activeRoleId.isBlank()) {
            return PortalUserSecurityUtils.isCurrentSessionSelfService() ? getUserBusinessRoleCodes(userId) : Set.of();
        }
        return matchKeysForActiveRole(userId, activeRoleId.trim());
    }

    private Set<String> matchKeysForActiveRole(String userId, String activeRoleId) {
        Set<String> keys = keysOfRole(fetchUserPortalRolePayloads(userId), activeRoleId);
        CachedData<List<Map<String, Object>>> cached = userRolePayloadsCache.get(userId);
        if (keys.isEmpty() && cached != null
                && System.currentTimeMillis() - cached.timestamp > ROLE_MISS_REFETCH_AFTER_MS) {
            // The session's role is newer than this replica's list (granted since, or the cache was cleared on
            // another replica when switching workspace): read the list again rather than refuse for the whole TTL.
            userRolePayloadsCache.remove(userId);
            keys = keysOfRole(fetchUserPortalRolePayloads(userId), activeRoleId);
        }
        return keys;
    }

    private static Set<String> keysOfRole(List<Map<String, Object>> rolePayloads, String activeRoleId) {
        Set<String> keys = new HashSet<>();
        for (Map<String, Object> role : rolePayloads) {
            String id = (String) role.get("id");
            if (!activeRoleId.equals(id)) {
                continue;
            }
            keys.add(id);
            String code = (String) role.get("code");
            if (code != null && !code.isBlank()) {
                keys.add(code);
            }
        }
        return keys;
    }

    /**
     * Cached per user (same TTL as the other role caches here, cleared on sign-in): the menu and the
     * All Requests scope run the FU check once per function unit. A failed fetch is not cached.
     */
    private List<Map<String, Object>> fetchUserPortalRolePayloads(String userId) {
        CachedData<List<Map<String, Object>>> cached = userRolePayloadsCache.get(userId);
        if (cached != null && !cached.isExpired()) {
            return cached.data;
        }
        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId)
                    + "/roles?" + USER_BUSINESS_ROLES_QUERY;
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            if (response.getBody() != null) {
                userRolePayloadsCache.put(userId, new CachedData<>(response.getBody()));
                return response.getBody();
            }
        } catch (Exception e) {
            // FALLBACK(external): admin-center roles API unavailable — New Request shows none
            log.error("Failed to list portal roles for user {}: {}", userId, e.getMessage(), e);
        }
        return Collections.emptyList();
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
     * Get the user's business role CODE list (for New-Request visibility filtering).
     * Falls back to role id when a role has no code (legacy/edge roles), so it stays
     * consistent with {@link #getFunctionUnitAllowedRoleCodes}'s identical fallback.
     */
    public Set<String> getUserBusinessRoleCodes(String userId) {
        CachedData<Set<String>> cached = userRoleCodesCache.get(userId);
        if (cached != null && !cached.isExpired()) {
            return cached.data;
        }

        try {
            String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/roles?" + USER_BUSINESS_ROLES_QUERY;
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            Set<String> roleCodes = new HashSet<>();
            if (response.getBody() != null) {
                for (Map<String, Object> role : response.getBody()) {
                    String roleCode = (String) role.get("code");
                    String key = (roleCode != null && !roleCode.isBlank())
                            ? roleCode
                            : (String) role.get("id");
                    if (key != null) {
                        roleCodes.add(key);
                    }
                }
            }

            userRoleCodesCache.put(userId, new CachedData<>(roleCodes));
            return roleCodes;

        } catch (Exception e) {
            log.error("Failed to get user business role codes for user {}: {}", userId, e.getMessage(), e);
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

        try {
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + SafeUrlInput.requirePathToken(functionUnitId) + "/access";
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

            return roleIds;

        } catch (Exception e) {
            log.error("Failed to get function unit access config for {}: {}", functionUnitId, e.getMessage(), e);
            return Collections.emptySet();
        }
    }

    /**
     * Get the role CODEs allowed to access a function unit (for New-Request visibility filtering).
     * Reads {@code targetCode} from the admin-center access response; falls back to {@code targetId}
     * when code is absent — matching {@link #getUserBusinessRoleCodes}'s fallback so both sides stay
     * comparable.
     */
    public Set<String> getFunctionUnitAllowedRoleCodes(String functionUnitId) {
        // Access config is always fetched live: Admin may add or remove roles at any time.
        try {
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + SafeUrlInput.requirePathToken(functionUnitId) + "/access";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            Set<String> roleCodes = new HashSet<>();
            if (response.getBody() != null) {
                for (Map<String, Object> access : response.getBody()) {
                    String targetType = (String) access.get("targetType");
                    if ("ROLE".equals(targetType)) {
                        String targetCode = (String) access.get("targetCode");
                        String key = (targetCode != null && !targetCode.isBlank())
                                ? targetCode
                                : (String) access.get("targetId");
                        if (key != null) {
                            roleCodes.add(key);
                        }
                    }
                }
            }

            return roleCodes;

        } catch (Exception e) {
            log.error("Failed to get function unit access codes for {}: {}", functionUnitId, e.getMessage(), e);
            return Collections.emptySet();
        }
    }

    /**
     * Role CODEs granted audit access to a function unit — i.e. permitted to review
     * every request of that unit without being its initiator or a participant.
     *
     * <p>Read from a table of its own, never from the launch-access list: that list
     * is filtered on {@code targetType} alone, so anything added to it is understood
     * as "may start this unit". Audit is strictly a read grant.
     */
    public Set<String> getFunctionUnitAuditRoleCodes(String functionUnitId) {
        // Live fetch, like the launch grants above: an admin may revoke review
        // rights at any moment and a cached yes would outlive the revocation.
        try {
            String url = adminCenterUrl + "/api/v1/admin/function-units/"
                    + SafeUrlInput.requirePathToken(functionUnitId) + "/audit-access";
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            Set<String> roleCodes = new HashSet<>();
            if (response.getBody() != null) {
                for (Map<String, Object> access : response.getBody()) {
                    if (!"ROLE".equals(access.get("targetType"))) {
                        continue;
                    }
                    String targetCode = (String) access.get("targetCode");
                    String key = (targetCode != null && !targetCode.isBlank())
                            ? targetCode
                            : (String) access.get("targetId");
                    if (key != null) {
                        roleCodes.add(key);
                    }
                }
            }
            return roleCodes;

        } catch (Exception e) {
            log.error("Failed to get function unit audit access codes for {}: {}",
                    functionUnitId, e.getMessage(), e);
            return Collections.emptySet();
        }
    }

    /**
     * Whether the user's <em>currently selected</em> workspace role is granted audit access to this
     * function unit. Covers what the All Requests menu lists, which requests may be opened, and
     * writing a note as an auditor: all of them follow the role the user switched to, never the
     * user's other roles (platform access model §7.2). Mirrors {@link #resolveNewRequestRoleKeys}:
     * without a workspace role (no UBR context) there is no selection to honour, so it falls back to
     * all portal roles.
     *
     * @param functionUnitIdOrCode function unit id or code
     */
    public boolean canAuditFunctionUnit(String userId, String functionUnitIdOrCode) {
        if (userId == null || userId.isBlank() || functionUnitIdOrCode == null || functionUnitIdOrCode.isBlank()) {
            return false;
        }
        String resolvedId = resolveFunctionUnitId(functionUnitIdOrCode);
        if (resolvedId == null) {
            log.warn("Audit check denied: cannot resolve function unit '{}'", functionUnitIdOrCode);
            return false;
        }
        Set<String> auditRoleCodes = getFunctionUnitAuditRoleCodes(resolvedId);
        if (auditRoleCodes.isEmpty()) {
            log.debug("Active-role audit check denied for user {}: function unit {} has no audit grants",
                    userId, functionUnitIdOrCode);
            return false;
        }
        String activeRoleId = PortalUserSecurityUtils.getCurrentActiveRoleId().orElse(null);
        Set<String> roleKeys = resolveNewRequestRoleKeys(userId, activeRoleId);
        for (String roleKey : roleKeys) {
            if (auditRoleCodes.contains(roleKey)) {
                return true;
            }
        }
        log.debug("Active-role audit check denied for user {}: active role keys {} do not intersect "
                + "audit grants {} of unit {}", userId, roleKeys, auditRoleCodes, functionUnitIdOrCode);
        return false;
    }

    /**
     * Clear user role cache
     */
    public void clearUserRolesCache(String userId) {
        userRolePayloadsCache.remove(userId);
        userRoleCodesCache.remove(userId);
    }
    
    /**
     * No-op: function unit access is fetched live (not cached).
     */
    public void clearFunctionUnitAccessCache(String functionUnitId) {
        // intentionally empty
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
        userRolePayloadsCache.clear();
        userRoleCodesCache.clear();
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
        /** Per-entry TTL; defaults to {@link #CACHE_TTL} so existing call sites are unchanged. */
        final long ttl;

        CachedData(T data) {
            this(data, CACHE_TTL);
        }

        CachedData(T data, long ttl) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.ttl = ttl;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }
}
