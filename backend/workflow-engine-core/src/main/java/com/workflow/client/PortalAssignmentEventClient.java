package com.workflow.client;

import com.platform.common.constant.PlatformConstants;
import com.workflow.config.RestTemplateConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Tells the Portal about assignment changes the engine makes on its own, so they reach the task's change history
 * ({@code up_change_history} belongs to the Portal), with the system as operator: a claimed pool task that went back to
 * the pool because its holder lost the BU + Role (D-40), and a task handed to the manager of people who left (D-42).
 */
@Slf4j
@Component
public class PortalAssignmentEventClient {

    static final String EVENTS_PATH = "/api/portal/internal/task-assignment-events";
    /** The Portal takes at most this many events of a kind per request. */
    static final int BATCH = 1000;

    private final RestTemplate restTemplate;
    private final String eventsUrl;
    private final String serviceToken;

    public PortalAssignmentEventClient(@Qualifier(RestTemplateConfig.INTERNAL_API_REST_TEMPLATE) RestTemplate restTemplate,
                                       @Value("${user-portal.url}") String userPortalUrl,
                                       @Value("${service.internal-token}") String serviceToken) {
        this.restTemplate = restTemplate;
        this.eventsUrl = userPortalUrl.trim() + EVENTS_PATH;
        this.serviceToken = serviceToken;
    }

    /**
     * A pool task released because its holder no longer holds the pool's BU + Role.
     *
     * @param endedDelegate the delegate of the holder's single-task delegation that ended with the hold (D-59), or null
     */
    public record PoolMembershipRelease(String taskId, String processInstanceId, String previousAssigneeId,
                                        String businessUnitCode, List<String> roleCodes, Instant releasedAt,
                                        String endedDelegate) {
    }

    /**
     * A task handed to the manager of the people who left (D-42).
     *
     * @param previousAssigneeId the holder who left, or null for an unclaimed list of named people who all left
     * @param endedDelegate      the delegate of the leaver's single-task delegation that ended (D-59), or null
     */
    public record DepartedReassignment(String taskId, String processInstanceId, String previousAssigneeId,
                                       String newAssigneeId, Instant reassignedAt, String endedDelegate) {
    }

    /** Request body of {@link #EVENTS_PATH}. */
    public record AssignmentEvents(List<PoolMembershipRelease> poolMembershipReleases,
                                   List<DepartedReassignment> departedReassignments) {
    }

    public void reportPoolMembershipReleases(List<PoolMembershipRelease> releases) {
        for (int from = 0; from < releases.size(); from += BATCH) {
            List<PoolMembershipRelease> batch = releases.subList(from, Math.min(from + BATCH, releases.size()));
            report(new AssignmentEvents(batch, List.of()), "pool-membership releases",
                    batch.stream().map(PoolMembershipRelease::taskId).toList());
        }
    }

    public void reportDepartedReassignments(List<DepartedReassignment> reassignments) {
        for (int from = 0; from < reassignments.size(); from += BATCH) {
            List<DepartedReassignment> batch = reassignments.subList(from, Math.min(from + BATCH, reassignments.size()));
            report(new AssignmentEvents(List.of(), batch), "reassignments to managers of people who left",
                    batch.stream().map(DepartedReassignment::taskId).toList());
        }
    }

    private void report(AssignmentEvents events, String what, List<String> taskIds) {
        if (taskIds.isEmpty()) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(PlatformConstants.HEADER_SERVICE_TOKEN, serviceToken);
        try {
            restTemplate.postForEntity(eventsUrl, new HttpEntity<>(events, headers), Void.class);
        } catch (RuntimeException e) {
            // FALLBACK(external): audit bypass (error-handling-governance whitelist) — the changes are committed in
            // the engine; only their change-history rows are missing. Logged with every task id for follow-up.
            log.error("Portal did not record {} {} (tasks {}): {}", taskIds.size(), what, taskIds, e.getMessage(), e);
        }
    }
}
