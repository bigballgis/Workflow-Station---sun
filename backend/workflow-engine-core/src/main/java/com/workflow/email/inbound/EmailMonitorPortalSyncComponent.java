package com.workflow.email.inbound;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * After email monitor starts a process in Flowable, proactively hydrates {@code up_process_instance}
 * in user-portal so My Request / application lists show the row without opening task detail first.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailMonitorPortalSyncComponent {

    private final RestTemplate restTemplate;

    @Value("${user-portal.url:http://user-portal:8080}")
    private String userPortalUrl;

    @Value("${portal.internal.api-token:${PORTAL_INTERNAL_API_TOKEN:}}")
    private String portalInternalApiToken;

    public void hydratePortalProcessInstanceAsync(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                // Allow the engine start transaction to commit before portal reads variables/tasks.
                Thread.sleep(500);
                if (!StringUtils.hasText(portalInternalApiToken)) {
                    log.warn("portal.internal.api-token not configured; skip portal hydrate for {}", processInstanceId);
                    return;
                }
                String url = userPortalUrl + "/api/portal/internal/runtime/hydrate-process-instance";
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Token", portalInternalApiToken);
                restTemplate.postForObject(
                        url,
                        new HttpEntity<>(Map.of("processInstanceId", processInstanceId), headers),
                        Map.class);
                log.info("Requested portal hydrate for email-started process {}", processInstanceId);
            } catch (Exception e) {
                log.warn("Portal hydrate failed for email-started process {}: {}", processInstanceId, e.getMessage());
            }
        });
    }
}
