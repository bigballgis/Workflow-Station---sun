package com.workflow.controller;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import com.workflow.assignment.AssignmentSubTablePlanner;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/assignment/sub-table-plans")
public class AssignmentSubTablePlanController {
    private final AssignmentSubTablePlanner planner;
    @Value("${service.internal-token:}") private String serviceToken;
    public AssignmentSubTablePlanController(AssignmentSubTablePlanner planner) { this.planner=planner; }
    @PostMapping public ApiResponse<AssignmentSubTablePlan> prepare(@RequestHeader(value="X-Internal-Token",required=false) String token,
            @Valid @RequestBody AssignmentSubTablePlan.Request request) {
        AssignmentServiceAuthentication.require(serviceToken,token);
        return ApiResponse.success(planner.prepare(request));
    }
}
