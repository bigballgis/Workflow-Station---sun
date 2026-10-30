package com.workflow.controller;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import com.workflow.assignment.AssignmentActionAvailabilityRead;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/assignment/action-availability")
public class AssignmentActionAvailabilityController {
    private final AssignmentActionAvailabilityRead reads;
    @Value("${service.internal-token:}") private String token;
    public AssignmentActionAvailabilityController(AssignmentActionAvailabilityRead reads) { this.reads=reads; }
    @PostMapping("/{task}/handoff") public ApiResponse<AssignmentActionAvailability> handoff(@PathVariable String task,
            @RequestHeader(value="X-Internal-Token",required=false) String supplied,@Valid @RequestBody Request request) {
        AssignmentServiceAuthentication.require(token,supplied);
        return ApiResponse.success(reads.handoff(task,request.actor()));
    }
    @PostMapping("/{source}/reopen") public ApiResponse<AssignmentActionAvailability> reopen(@PathVariable String source,
            @RequestHeader(value="X-Internal-Token",required=false) String supplied,@Valid @RequestBody Request request) {
        AssignmentServiceAuthentication.require(token,supplied);
        return ApiResponse.success(reads.reopen(source,request.actor()));
    }
    public record Request(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) String actor) { }
}
