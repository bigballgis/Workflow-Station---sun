package com.workflow.controller;

import com.platform.common.assignment.AssignmentServiceAuthentication;
import com.platform.common.assignment.AssignmentWriteDecision;
import com.platform.common.dto.ApiResponse;
import com.workflow.assignment.AssignmentWriteDecisionComponent;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/assignment/write-decisions")
public class AssignmentWriteDecisionController {
    private final AssignmentWriteDecisionComponent component;
    @Value("${service.internal-token:}") private String serviceToken;
    public AssignmentWriteDecisionController(AssignmentWriteDecisionComponent component) { this.component = component; }
    @PostMapping public ApiResponse<AssignmentWriteDecision> prepare(@RequestHeader(value="X-Internal-Token", required=false) String token,
            @Valid @RequestBody AssignmentWriteDecision.Request request) {
        AssignmentServiceAuthentication.require(serviceToken, token);
        return ApiResponse.success(component.prepare(request));
    }
}
