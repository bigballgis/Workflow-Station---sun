package com.workflow.controller;

import com.platform.common.assignment.AssignmentPostAction;
import com.platform.common.assignment.AssignmentServiceAuthentication;
import com.platform.common.dto.ApiResponse;
import com.workflow.assignment.AssignmentPostActionEnqueue;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/assignment/post-actions")
public class AssignmentPostActionController {
    private final AssignmentPostActionEnqueue component;
    @Value("${service.internal-token:}") private String serviceToken;
    public AssignmentPostActionController(AssignmentPostActionEnqueue component) { this.component=component; }
    @PostMapping public ApiResponse<Boolean> enqueue(@RequestHeader(value="X-Internal-Token",required=false) String token,
            @Valid @RequestBody AssignmentPostAction.Enqueue request) {
        AssignmentServiceAuthentication.require(serviceToken,token);
        component.enqueue(request);
        return ApiResponse.success(true);
    }
}
