package com.portal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Assignment changes the engine made on its own, for the change history (platform access model D-40, D-42), each with
 * the delegate of the single-task delegation it ended, if any (D-59). Sent by the engine's
 * {@code PortalAssignmentEventClient}; a list the engine leaves out counts as empty.
 */
public record TaskAssignmentEventsRequest(
        @Size(max = 1000) List<@Valid PoolMembershipRelease> poolMembershipReleases,
        @Size(max = 1000) List<@Valid DepartedReassignment> departedReassignments) {

    public TaskAssignmentEventsRequest {
        poolMembershipReleases = poolMembershipReleases == null ? List.of() : poolMembershipReleases;
        departedReassignments = departedReassignments == null ? List.of() : departedReassignments;
    }

    /** A claimed pool task released because its holder no longer holds the pool's BU + Role. */
    public record PoolMembershipRelease(@NotBlank @Size(max = 64) String taskId,
                                        @NotBlank @Size(max = 64) String processInstanceId,
                                        @NotBlank @Size(max = 64) String previousAssigneeId,
                                        @Size(max = 64) String businessUnitCode,
                                        List<String> roleCodes,
                                        Instant releasedAt,
                                        @Size(max = 200) String endedDelegate) {
    }

    /** A task handed to the manager of the people who left; no previous holder for an unclaimed list (D-42). */
    public record DepartedReassignment(@NotBlank @Size(max = 64) String taskId,
                                       @NotBlank @Size(max = 64) String processInstanceId,
                                       @Size(max = 64) String previousAssigneeId,
                                       @NotBlank @Size(max = 64) String newAssigneeId,
                                       Instant reassignedAt,
                                       @Size(max = 200) String endedDelegate) {
    }
}
