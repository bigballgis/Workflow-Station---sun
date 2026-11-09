package com.admin.client;

import com.admin.dto.request.ClaimPoolResyncRequest;
import com.platform.common.constant.PlatformConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Engine calls Admin Center makes after a committed change that moves tasks (platform access model WP8b): BU + Role
 * claim pools follow the current UBR (WP8b-2), and the tasks of a user who left go to their manager (D-42).
 *
 * <p>Service-to-service only: there is no user on whose behalf the engine acts, so the calls carry
 * {@code X-Service-Token} and no user identity.
 */
@Component
public class EngineInternalClient {

    static final String RESYNC_PATH = "/api/v1/internal/claim-pools/resync";
    static final String DEPARTED_PATH = "/api/v1/internal/departed-users/tasks/reassign";

    private final RestTemplate restTemplate;
    private final String engineBaseUrl;
    private final String serviceToken;

    public EngineInternalClient(RestTemplate restTemplate,
                                @Value("${workflow-engine.url}") String engineBaseUrl,
                                @Value("${service.internal-token}") String serviceToken) {
        if (!StringUtils.hasText(engineBaseUrl)) {
            throw new IllegalStateException("workflow-engine.url must be configured for engine internal calls");
        }
        if (!StringUtils.hasText(serviceToken)) {
            throw new IllegalStateException("service.internal-token must be configured (SERVICE_INTERNAL_TOKEN)");
        }
        this.restTemplate = restTemplate;
        this.engineBaseUrl = engineBaseUrl.trim();
        this.serviceToken = serviceToken;
    }

    /**
     * Asks the engine to recompute the candidates of the open tasks in these pools.
     *
     * @throws org.springframework.web.client.RestClientException when the engine cannot be reached or answers 4xx/5xx
     * @throws IllegalStateException when the engine answers without acknowledging the resync
     */
    public void resyncClaimPools(List<ClaimPoolResyncRequest.Pool> pools) {
        post(RESYNC_PATH, new ClaimPoolResyncRequest(pools), "claim-pool resync");
    }

    /**
     * Asks the engine to hand the tasks of these users, now INACTIVE or deleted, to their managers.
     *
     * @throws org.springframework.web.client.RestClientException when the engine cannot be reached or answers 4xx/5xx
     * @throws IllegalStateException when the engine answers without acknowledging the reassignment
     */
    public void reassignTasksOfDepartedUsers(List<String> userIds) {
        post(DEPARTED_PATH, Map.of("userIds", userIds), "reassignment of departed users' tasks");
    }

    private void post(String path, Object body, String what) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(PlatformConstants.HEADER_SERVICE_TOKEN, serviceToken);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(engineBaseUrl + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), new ParameterizedTypeReference<Map<String, Object>>() {});
        Map<String, Object> answer = response.getBody();
        if (answer == null || !Boolean.TRUE.equals(answer.get("success"))) {
            throw new IllegalStateException("Engine did not acknowledge the " + what + " (HTTP "
                    + response.getStatusCode().value() + ")");
        }
    }
}
