package com.portal.controller;

import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.ApiResponse;
import com.platform.security.util.ServiceTokenVerifier;
import com.portal.component.TaskAssignmentHistoryRecorder;
import com.portal.dto.TaskAssignmentEventsRequest;
import com.portal.entity.ChangeHistory;
import com.portal.enums.AssignmentChangeTypes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * The engine reports assignment changes it made on its own, so they reach the change history: a claimed BU + Role pool
 * task released because the holder no longer holds that BU + Role (D-40), and a task handed to the manager of people
 * who left (D-42); either one also ends the holder's single-task delegation, recorded as {@code DELEGATION_ENDED} /
 * {@code HOLDER_CHANGED} (D-59). Service token only: no user acts here.
 */
@Slf4j
@RestController
@RequestMapping("/internal/task-assignment-events")
@RequiredArgsConstructor
public class InternalTaskAssignmentEventController {

    private final ServiceTokenVerifier serviceTokenVerifier;
    private final TaskAssignmentHistoryRecorder taskAssignmentHistoryRecorder;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> record(
            @RequestHeader(value = PlatformConstants.HEADER_SERVICE_TOKEN, required = false) String serviceToken,
            @Valid @RequestBody TaskAssignmentEventsRequest request) {
        if (!serviceTokenVerifier.matches(serviceToken)) {
            log.warn("Task assignment events refused without a valid service token");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("PERM_ACCESS_DENIED", "Forbidden: valid service token required"));
        }
        List<ChangeHistory> rows = new ArrayList<>();
        for (TaskAssignmentEventsRequest.PoolMembershipRelease release : request.poolMembershipReleases()) {
            rows.add(TaskAssignmentHistoryRecorder.poolMembershipRelease(release.processInstanceId(), release.taskId(),
                    release.previousAssigneeId(), release.releasedAt()));
            addDelegationEnded(rows, release.processInstanceId(), release.taskId(), release.endedDelegate(),
                    release.releasedAt());
        }
        for (TaskAssignmentEventsRequest.DepartedReassignment reassignment : request.departedReassignments()) {
            rows.add(TaskAssignmentHistoryRecorder.departedReassignment(reassignment.processInstanceId(),
                    reassignment.taskId(), reassignment.previousAssigneeId(), reassignment.newAssigneeId(),
                    reassignment.reassignedAt()));
            addDelegationEnded(rows, reassignment.processInstanceId(), reassignment.taskId(),
                    reassignment.endedDelegate(), reassignment.reassignedAt());
        }
        // A failure propagates (HTTP 500): the engine then logs every task id it could not record.
        taskAssignmentHistoryRecorder.recordSystemEvents(rows);
        log.info("Recorded {} pool-membership releases and {} reassignments to managers from the engine",
                request.poolMembershipReleases().size(), request.departedReassignments().size());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** The holder's single-task delegation ended with the system's change (D-59): one more row, by {@code system}. */
    private static void addDelegationEnded(List<ChangeHistory> rows, String processInstanceId, String taskId,
                                           String endedDelegate, java.time.Instant at) {
        if (endedDelegate != null && !endedDelegate.isBlank()) {
            rows.add(TaskAssignmentHistoryRecorder.delegationEnded(processInstanceId, taskId, endedDelegate.trim(),
                    AssignmentChangeTypes.SYSTEM_ACTOR, at, com.portal.dto.ChangeHistoryContext.builder().build()));
        }
    }
}
