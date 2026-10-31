package com.workflow.controller;

import com.platform.common.assignment.AssignmentServiceAuthentication;
import com.platform.common.dto.ApiResponse;
import com.workflow.assignment.AssignmentHandoffComponent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/assignment/handoffs")
public class AssignmentHandoffController {
    private final AssignmentHandoffComponent component;
    @Value("${service.internal-token:}") private String token;
    public AssignmentHandoffController(AssignmentHandoffComponent component) { this.component=component; }
    @PostMapping("/{task}") public ApiResponse<Boolean> handoff(@PathVariable String task,
            @RequestHeader(value="X-Internal-Token",required=false) String provided,@Valid @RequestBody Request request) {
        AssignmentServiceAuthentication.require(token,provided);
        return ApiResponse.success(component.handoff(task,request.actor(),request.target()));
    }
    public record Request(@NotBlank @Size(max=64) String actor,@NotBlank @Size(max=64) String target) { }
}
