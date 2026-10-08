package com.admin.component;

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

import java.util.HashMap;
import java.util.Map;

/**
 * Internal REST client that asks user-portal to start an SLA due date recalculation job for one
 * Function Unit. The portal reads the lead time itself; the version only lets it drop a request
 * that a newer change already superseded.
 */
@Component
@RequiredArgsConstructor
public class PortalSlaRecalcClient {

    private final RestTemplate restTemplate;

    @Value("${user-portal.base-url:http://localhost:8082/api/portal}")
    private String userPortalBaseUrl;

    @Value("${user-portal.internal-api-token:}")
    private String userPortalInternalApiToken;

    /**
     * @return the portal job id
     * @throws IllegalStateException when the token is not configured or the portal rejects the call
     */
    public String submit(String functionUnitCode, int policyVersion, String triggeredBy) {
        if (userPortalInternalApiToken == null || userPortalInternalApiToken.isBlank()) {
            throw new IllegalStateException(
                    "user-portal.internal-api-token is not configured, cannot start SLA recalculation");
        }
        String base = userPortalBaseUrl != null ? userPortalBaseUrl.replaceAll("/$", "") : "";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", userPortalInternalApiToken);
        Map<String, Object> body = new HashMap<>();
        body.put("functionUnitCode", functionUnitCode);
        body.put("policyVersion", policyVersion);
        body.put("triggeredBy", triggeredBy);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                base + "/internal/sla/recalc-jobs",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        Object jobId = resp.getBody() == null ? null
                : ApiResponseBodyUnwrap.unwrapDataMap(resp.getBody()).get("jobId");
        if (!resp.getStatusCode().is2xxSuccessful() || jobId == null) {
            throw new IllegalStateException("Portal SLA recalculation returned " + resp.getStatusCode());
        }
        return String.valueOf(jobId);
    }
}
