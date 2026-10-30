package com.portal.controller;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.dto.ApiResponse;
import com.portal.client.AssignmentEngineTransport;
import com.portal.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tasks")
public class AssignmentHandoffController {
    private static final ParameterizedTypeReference<ApiResponse<Boolean>> BOOLEAN=new ParameterizedTypeReference<>() { };
    private final AssignmentEngineTransport engine;
    public AssignmentHandoffController(AssignmentEngineTransport engine) { this.engine=engine; }
    @PostMapping("/{task}/assignment/handoff") public ApiResponse<Boolean> handoff(@PathVariable String task,@CurrentUserId String actor,@Valid @RequestBody Request request) {
        com.portal.security.AssignmentPortalActor.require(actor);
        if (!Boolean.TRUE.equals(engine.post(List.of("handoffs",task),Map.of("actor",actor,"target",request.target()),BOOLEAN,false))) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE);
        }
        return ApiResponse.success(true);
    }
    public record Request(@NotBlank @Size(max=64) String target) { }
}
