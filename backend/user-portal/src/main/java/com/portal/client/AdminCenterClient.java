package com.portal.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.platform.common.util.ApiResponseBodyUnwrap;
import com.platform.common.util.SafeUrlInput;

import java.util.*;

/**
 * Admin Center REST client.
 * Provides typed methods for calling admin-center APIs from user-portal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminCenterClient {

    private final RestTemplate restTemplate;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    // ==================== Members ====================

    /** 移除用户在业务单元下的某一角色绑定 */
    public boolean removeUserBusinessUnitRole(String userId, String businessUnitId, String roleId) {
        return deleteVoid("/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/business-unit-roles/" + SafeUrlInput.requirePathToken(businessUnitId) + "/" + SafeUrlInput.requirePathToken(roleId));
    }

    public Optional<Map<String, Object>> getUserMemberships(String userId) {
        return get("/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/memberships");
    }

    // ==================== Claim pool ====================

    /**
     * FAIL-CLOSED(security): missing/failed admin-center response means no force-unclaim flags.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Boolean> evaluateForceUnclaim(String userId, List<Map<String, Object>> items) {
        if (userId == null || userId.isBlank() || items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("items", items);
        Optional<Map<String, Object>> data = post("/api/v1/admin/force-unclaim/evaluate", body);
        if (data.isEmpty()) {
            return Map.of();
        }
        Object flagsObj = data.get().get("flags");
        if (!(flagsObj instanceof Map<?, ?> rawFlags)) {
            return Map.of();
        }
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawFlags.entrySet()) {
            if (entry.getKey() != null) {
                flags.put(entry.getKey().toString(), Boolean.TRUE.equals(entry.getValue()));
            }
        }
        return flags;
    }

    // ==================== Internal HTTP helpers ====================

    private Optional<Map<String, Object>> get(String path) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    adminCenterUrl + path, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> raw = response.getBody();
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(raw));
        } catch (Exception e) {
            log.warn("Admin center GET {} failed: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Map<String, Object>> post(String path, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    adminCenterUrl + path, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> raw = response.getBody();
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(ApiResponseBodyUnwrap.unwrapDataMap(raw));
        } catch (Exception e) {
            log.warn("Admin center POST {} failed: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean deleteVoid(String path) {
        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    adminCenterUrl + path, HttpMethod.DELETE, null, Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Admin center DELETE {} failed: {}", path, e.getMessage());
            return false;
        }
    }
}
