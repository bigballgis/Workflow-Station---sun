package com.portal.controller;

import com.platform.common.assignment.AssignmentReopenReceipt;
import com.platform.common.dto.ApiResponse;
import com.portal.component.AssignmentReopenPortal;
import com.portal.security.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/processes")
public class AssignmentReopenController {
    private final AssignmentReopenPortal component;
    public AssignmentReopenController(AssignmentReopenPortal component) { this.component = component; }
    @PostMapping("/{source}/reopen")
    public ApiResponse<AssignmentReopenReceipt.Result> reopen(@PathVariable String source, @CurrentUserId String actor, @Valid @RequestBody Request request) {
        com.portal.security.AssignmentPortalActor.require(actor);
        return ApiResponse.success(component.reopen(source, request.operationId(), actor));
    }
    public record Request(@jakarta.validation.constraints.NotNull UUID operationId) { }
}
