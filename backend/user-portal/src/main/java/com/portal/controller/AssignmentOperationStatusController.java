package com.portal.controller;

import com.platform.common.dto.ApiResponse;
import com.portal.security.CurrentUserId;
import com.portal.component.AssignmentCompletionOrchestrator;
import com.portal.exception.PortalException;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Browser status never contains a reusable authorization decision or the private accepted business effect. */
@RestController
@RequestMapping("/tasks/assignment/operations")
public class AssignmentOperationStatusController {
    private final AssignmentCompletionOrchestrator completion;
    public AssignmentOperationStatusController(AssignmentCompletionOrchestrator completion) { this.completion=completion; }
    @GetMapping("/{operation}") public ApiResponse<AssignmentCompletionOrchestrator.Status> status(
            @PathVariable UUID operation,@CurrentUserId String actor) {
        if (actor==null || actor.isBlank()) throw new PortalException("401","Authentication required");
        com.portal.security.AssignmentPortalActor.require(actor);
        return ApiResponse.success(completion.status(operation,actor));
    }
}
