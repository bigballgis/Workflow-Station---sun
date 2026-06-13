package com.admin.component;

import com.admin.exception.AdminBusinessException;
import com.platform.common.util.ApiResponseBodyUnwrap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Internal REST client for user-portal runtime data cleanup
 * (rollback/deprecate flows purge engine data by catalog id).
 */
@Component
@RequiredArgsConstructor
public class PortalRuntimePurgeClient {

    private final RestTemplate restTemplate;

    @Value("${user-portal.base-url:http://localhost:8082/api/portal}")
    private String userPortalBaseUrl;

    @Value("${user-portal.internal-api-token:}")
    private String userPortalInternalApiToken;

    /**
     * Purge portal runtime data by catalog id (engine purge) for rollback/deprecate flows
     */
    public Map<String, Object> purgeRuntimeDataForCatalog(String catalogId) {
        if (userPortalInternalApiToken == null || userPortalInternalApiToken.isBlank()) {
            throw new AdminBusinessException("CONFIG", "user-portal.internal-api-token is not configured, cannot invoke portal cleanup for runtime data");
        }
        String base = userPortalBaseUrl != null ? userPortalBaseUrl.replaceAll("/$", "") : "";
        String url = base + "/internal/runtime/purge-by-catalog";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", userPortalInternalApiToken);
        Map<String, String> body = Map.of("catalogId", catalogId);
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new AdminBusinessException("PORTAL_PURGE_FAILED", "Portal cleanup returned error: " + resp.getStatusCode());
            }
            return ApiResponseBodyUnwrap.unwrapDataMap(resp.getBody());
        } catch (AdminBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new AdminBusinessException("PORTAL_PURGE_FAILED", "Failed to invoke portal cleanup: " + e.getMessage(), e);
        }
    }
}
