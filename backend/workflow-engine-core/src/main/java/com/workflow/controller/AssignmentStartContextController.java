package com.workflow.controller;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import com.workflow.assignment.AssignmentDefinitionPolicy;
import com.workflow.component.ProcessStartDefinitionResolver;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/assignment/start-context")
public class AssignmentStartContextController {
    private final ProcessStartDefinitionResolver definitions;
    private final AssignmentDefinitionPolicy policies;
    @Value("${service.internal-token:}") private String token;
    public AssignmentStartContextController(ProcessStartDefinitionResolver definitions,AssignmentDefinitionPolicy policies) {
        this.definitions=definitions; this.policies=policies;
    }
    @PostMapping public ApiResponse<AssignmentStartContext> read(@RequestHeader(value="X-Internal-Token",required=false) String supplied,
            @Valid @RequestBody AssignmentStartContext.Request request) {
        AssignmentServiceAuthentication.require(token,supplied);
        var definition=definitions.resolve(request.definitionKey(),request.expectedDefinitionId());
        return ApiResponse.success(new AssignmentStartContext(1,definition.getId(),definition.getKey(),policies.tracked(definition.getId()),request.expectedDefinitionId()));
    }
}
