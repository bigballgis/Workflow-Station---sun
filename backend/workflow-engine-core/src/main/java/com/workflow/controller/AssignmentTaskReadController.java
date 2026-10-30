package com.workflow.controller;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import com.workflow.assignment.AssignmentTaskReadComponent;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/assignment/task-reads")
public class AssignmentTaskReadController {
    private final AssignmentTaskReadComponent component;
    @Value("${service.internal-token:}") private String serviceToken;
    public AssignmentTaskReadController(AssignmentTaskReadComponent component) { this.component=component; }
    @PostMapping public ApiResponse<AssignmentTaskReadSnapshot> read(@RequestHeader(value="X-Internal-Token",required=false) String token,
            @Valid @RequestBody AssignmentTaskReadSnapshot.Request request) {
        AssignmentServiceAuthentication.require(serviceToken,token);
        return ApiResponse.success(component.read(request));
    }
}
