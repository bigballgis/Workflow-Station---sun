package com.admin.component;

import com.admin.dto.request.UserPortalAuditListQueryRequest;
import com.admin.exception.AdminBusinessException;
import com.platform.common.util.ApiResponseBodyUnwrap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal REST client for querying user-portal audit logs (up_change_history).
 * Follows the same pattern as {@link PortalRuntimePurgeClient}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPortalAuditClient {

    private final RestTemplate restTemplate;

    @Value("${user-portal.base-url:http://localhost:8082/api/portal}")
    private String userPortalBaseUrl;

    @Value("${user-portal.internal-api-token:}")
    private String userPortalInternalApiToken;

    /**
     * Query user portal audit logs with pagination and filters.
     */
    public Map<String, Object> queryAuditLogs(Map<String, Object> request) {
        requireTokenConfigured();
        String url = stripTrailingSlash(userPortalBaseUrl) + "/internal/audit-logs/query";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", userPortalInternalApiToken);

        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new AdminBusinessException("UP_AUDIT_QUERY_FAILED",
                        "User portal audit query returned error: " + resp.getStatusCode());
            }
            return ApiResponseBodyUnwrap.unwrapDataMap(resp.getBody());
        } catch (AdminBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query user portal audit logs: {}", e.getMessage(), e);
            throw new AdminBusinessException("UP_AUDIT_QUERY_FAILED",
                    "Failed to query user portal audit logs: " + e.getMessage(), e);
        }
    }

    /**
     * Query user portal audit logs with true paging, column filters, sort and grouping.
     */
    public Map<String, Object> queryAuditLogList(UserPortalAuditListQueryRequest request) {
        requireTokenConfigured();
        String url = stripTrailingSlash(userPortalBaseUrl) + "/internal/audit-logs/list-query";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", userPortalInternalApiToken);

        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new AdminBusinessException("UP_AUDIT_QUERY_FAILED",
                        "User portal audit list-query returned error: " + resp.getStatusCode());
            }
            return ApiResponseBodyUnwrap.unwrapDataMap(resp.getBody());
        } catch (AdminBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query user portal audit logs: {}", e.getMessage(), e);
            throw new AdminBusinessException("UP_AUDIT_QUERY_FAILED",
                    "Failed to query user portal audit logs: " + e.getMessage(), e);
        }
    }

    /**
     * Get distinct function unit codes (with display names) that have audit data.
     */
    public List<Map<String, String>> getFunctionUnitCodes() {
        requireTokenConfigured();
        String url = stripTrailingSlash(userPortalBaseUrl) + "/internal/audit-logs/function-units";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", userPortalInternalApiToken);

        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new AdminBusinessException("UP_AUDIT_FU_QUERY_FAILED",
                        "User portal function-units query returned error: " + resp.getStatusCode());
            }
            Object data = resp.getBody().get("data");
            if (data instanceof List<?> list) {
                List<Map<String, String>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m && m.get("code") != null) {
                        Map<String, String> entry = new HashMap<>();
                        entry.put("code", String.valueOf(m.get("code")));
                        entry.put("name", m.get("name") != null ? String.valueOf(m.get("name")) : null);
                        result.add(entry);
                    }
                }
                return result;
            }
            return Collections.emptyList();
        } catch (AdminBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get user portal audit function units: {}", e.getMessage(), e);
            throw new AdminBusinessException("UP_AUDIT_FU_QUERY_FAILED",
                    "Failed to get user portal audit function units: " + e.getMessage(), e);
        }
    }

    private void requireTokenConfigured() {
        if (userPortalInternalApiToken == null || userPortalInternalApiToken.isBlank()) {
            throw new AdminBusinessException("CONFIG",
                    "user-portal.internal-api-token is not configured, cannot query user portal audit logs");
        }
    }

    private static String stripTrailingSlash(String url) {
        return url != null ? url.replaceAll("/$", "") : "";
    }
}
