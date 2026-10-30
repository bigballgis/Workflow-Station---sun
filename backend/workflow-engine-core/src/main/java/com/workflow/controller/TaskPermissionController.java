package com.workflow.controller;

import com.workflow.assignment.AssignmentTaskPermissionRead;
import com.workflow.component.TaskManagerComponent;
import com.workflow.dto.response.ApiResponse;
import com.workflow.service.UserPermissionService;
import com.workflow.util.WorkflowActorResolver;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Existing permission endpoint keeps its original role check and adds the responsibility veto. */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskPermissionController {
    private final TaskManagerComponent tasks;
    private final UserPermissionService permissions;
    private final AssignmentTaskPermissionRead assignment;
    public TaskPermissionController(TaskManagerComponent tasks,UserPermissionService permissions,AssignmentTaskPermissionRead assignment) {
        this.tasks=tasks; this.permissions=permissions; this.assignment=assignment;
    }
    @GetMapping("/{taskId}/check-permission")
    public ResponseEntity<ApiResponse<Map<String,Object>>> check(@PathVariable String taskId,@RequestParam("userId") String userId) {
        var actor=WorkflowActorResolver.currentUserId();
        if (actor.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("UNAUTHORIZED","Authentication required"));
        if (!actor.get().equals(userId)) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN","Can only check permissions for the authenticated user"));
        var task=tasks.getTaskInfo(taskId);
        boolean existing=permissions.hasTaskPermission(userId,task.getAssignmentType(),task.getAssignmentTarget());
        var access=assignment.read(taskId,userId);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("taskId",taskId); result.put("userId",userId);
        result.put("hasPermission",existing && access.exclusionAllowsWrite());
        result.put("assignmentType",task.getAssignmentType()); result.put("assignmentTarget",task.getAssignmentTarget());
        result.put("assignmentAccess",access);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
