package com.workflow.controller;

import com.platform.common.assignment.AssignmentServiceAuthentication;
import com.platform.common.dto.ApiResponse;
import com.workflow.assignment.AssignmentRuntimeSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/assignment/capabilities")
public class AssignmentCapabilityController {
    private final AssignmentRuntimeSupport support;
    @Value("${service.internal-token:}") private String token;
    public AssignmentCapabilityController(AssignmentRuntimeSupport support) { this.support=support; }
    @GetMapping public ApiResponse<AssignmentRuntimeSupport.Capability> read(@RequestHeader(value="X-Internal-Token",required=false) String supplied) {
        AssignmentServiceAuthentication.require(token,supplied);
        return ApiResponse.success(support.capability());
    }
}
