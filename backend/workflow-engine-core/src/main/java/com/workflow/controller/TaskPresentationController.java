package com.workflow.controller;

import com.platform.common.assignment.AssignmentServiceAuthentication;
import com.platform.common.dto.ApiResponse;
import com.workflow.component.TaskPresentationReader;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/assignment/task-presentations")
public class TaskPresentationController {
    private final TaskPresentationReader reader;
    @Value("${service.internal-token:}") private String token;
    public TaskPresentationController(TaskPresentationReader reader) { this.reader=reader; }
    @PostMapping public ApiResponse<List<TaskPresentationReader.Entry>> read(
            @RequestHeader(value="X-Internal-Token",required=false) String supplied,
            @Valid @RequestBody TaskPresentationReader.Request request) {
        AssignmentServiceAuthentication.require(token,supplied);
        return ApiResponse.success(reader.read(request));
    }
}
