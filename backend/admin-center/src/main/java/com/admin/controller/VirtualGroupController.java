package com.admin.controller;

import com.admin.component.VirtualGroupManagerComponent;
import com.admin.dto.request.TaskClaimRequest;
import com.admin.dto.request.TaskDelegationRequest;
import com.admin.dto.request.VirtualGroupCreateRequest;
import com.admin.dto.request.VirtualGroupMemberRequest;
import com.admin.dto.response.*;
import com.admin.service.VirtualGroupTaskService;
import com.platform.security.util.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.platform.common.i18n.I18nService;

/**
 * Virtual Group Management RESTful API
 */
@Slf4j
@RestController
@RequestMapping("/virtual-groups")
@RequiredArgsConstructor
@Tag(name = "Virtual Group Management", description = "Virtual group creation, member management and task query APIs")
public class VirtualGroupController {
    
    private final VirtualGroupManagerComponent virtualGroupManager;
    private final VirtualGroupTaskService virtualGroupTaskService;
    private final I18nService i18nService;
    
    // ==================== Virtual Group CRUD ====================
    
    @PostMapping
    @Operation(summary = "Create virtual group", description = "Create a new virtual group")
    public ResponseEntity<VirtualGroupResult> createVirtualGroup(
            @Valid @RequestBody VirtualGroupCreateRequest request) {
        log.info("Creating virtual group: {}", request.getName());
        VirtualGroupResult result = virtualGroupManager.createVirtualGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
    
    @GetMapping
    @Operation(summary = "Get virtual group list", description = "Get all virtual groups")
    public ResponseEntity<List<VirtualGroupInfo>> listVirtualGroups(
            @Parameter(description = "Virtual group type") @RequestParam(required = false) String type,
            @Parameter(description = "Status") @RequestParam(required = false) String status) {
        log.info("Listing virtual groups, type: {}, status: {}", type, status);
        List<VirtualGroupInfo> groups = virtualGroupManager.listVirtualGroups(type, status);
        return ResponseEntity.ok(groups);
    }
    
    @GetMapping("/{groupId}")
    @Operation(summary = "Get virtual group detail", description = "Get virtual group detail by ID")
    public ResponseEntity<VirtualGroupInfo> getVirtualGroup(
            @Parameter(description = "Virtual group ID") @PathVariable String groupId) {
        log.info("Getting virtual group: {}", groupId);
        VirtualGroupInfo group = virtualGroupManager.getVirtualGroup(groupId);
        return ResponseEntity.ok(group);
    }
    
    @PutMapping("/{groupId}")
    @Operation(summary = "Update virtual group", description = "Update virtual group info")
    public ResponseEntity<VirtualGroupResult> updateVirtualGroup(
            @Parameter(description = "Virtual group ID") @PathVariable String groupId,
            @Valid @RequestBody VirtualGroupCreateRequest request) {
        log.info("Updating virtual group: {}", groupId);
        VirtualGroupResult result = virtualGroupManager.updateVirtualGroup(groupId, request);
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{groupId}")
    @Operation(summary = "Delete virtual group", description = "Delete virtual group")
    public ResponseEntity<Void> deleteVirtualGroup(
            @Parameter(description = "Virtual group ID") @PathVariable String groupId) {
        log.info("Deleting virtual group: {}", groupId);
        virtualGroupManager.deleteVirtualGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    
    // ==================== Member Management ====================
    
    @GetMapping("/{groupId}/members")
    @Operation(summary = "Get virtual group members", description = "Get all members of a virtual group")
    public ResponseEntity<List<VirtualGroupMemberInfo>> getGroupMembers(
            @Parameter(description = "Virtual group ID") @PathVariable String groupId) {
        log.info("Getting members of virtual group: {}", groupId);
        List<VirtualGroupMemberInfo> members = virtualGroupManager.getGroupMembers(groupId);
        return ResponseEntity.ok(members);
    }
    
    @PostMapping("/{groupId}/members")
    @Operation(summary = "Add member", description = "Add member to virtual group")
    public ResponseEntity<VirtualGroupResult> addMember(
            @Parameter(description = "Virtual group ID") @PathVariable String groupId,
            @Valid @RequestBody VirtualGroupMemberRequest request) {
        log.info("Adding member {} to virtual group: {}", request.getUserId(), groupId);
        VirtualGroupResult result = virtualGroupManager.addMember(groupId, request);
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{groupId}/members/{userId}")
    @Operation(summary = "Remove member", description = "Remove member from virtual group")
    public ResponseEntity<VirtualGroupResult> removeMember(
            @Parameter(description = "Virtual group ID") @PathVariable String groupId,
            @Parameter(description = "User ID") @PathVariable String userId) {
        log.info("Removing member {} from virtual group: {}", userId, groupId);
        VirtualGroupResult result = virtualGroupManager.removeMember(groupId, userId);
        return ResponseEntity.ok(result);
    }
    
    // ==================== Task Query ====================
    
    @GetMapping("/{groupId}/tasks")
    @Operation(summary = "Get virtual group tasks", description = "Get tasks assigned to the virtual group")
    public ResponseEntity<List<GroupTaskInfo>> getGroupTasks(
            @Parameter(description = "Virtual group ID") @PathVariable String groupId) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        log.info("Getting tasks for virtual group: {} by user: {}", groupId, userId);
        List<GroupTaskInfo> tasks = virtualGroupTaskService.getGroupTasks(groupId, userId);
        return ResponseEntity.ok(tasks);
    }
    
    @GetMapping("/my-tasks")
    @Operation(summary = "Get user-visible group tasks", description = "Get all virtual group tasks visible to current user")
    public ResponseEntity<List<GroupTaskInfo>> getUserVisibleGroupTasks() {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        log.info("Getting visible group tasks for user: {}", userId);
        List<GroupTaskInfo> tasks = virtualGroupTaskService.getUserVisibleGroupTasks(userId);
        return ResponseEntity.ok(tasks);
    }
    
    @GetMapping("/{groupId}/tasks/{taskId}/history")
    @Operation(summary = "Get task history", description = "Get task processing history")
    public ResponseEntity<List<TaskHistoryInfo>> getTaskHistory(
            @Parameter(description = "Virtual group ID") @PathVariable String groupId,
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        log.info("Getting history for task: {} in group: {}", taskId, groupId);
        List<TaskHistoryInfo> history = virtualGroupTaskService.getTaskHistory(taskId);
        return ResponseEntity.ok(history);
    }
    
    // ==================== Task Operations ====================
    
    @PostMapping("/{groupId}/tasks/{taskId}/claim")
    @Operation(summary = "Claim task", description = "Claim a virtual group task")
    public ResponseEntity<Void> claimTask(
            @Parameter(description = "Virtual group ID") @PathVariable String groupId,
            @Parameter(description = "Task ID") @PathVariable String taskId,
            @RequestBody(required = false) TaskClaimRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        log.info("User {} claiming task {} from group {}", userId, taskId, groupId);
        
        TaskClaimRequest claimRequest = request != null ? request : new TaskClaimRequest();
        claimRequest.setTaskId(taskId);
        claimRequest.setGroupId(groupId);
        
        virtualGroupTaskService.claimTask(userId, claimRequest);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/tasks/{taskId}/delegate")
    @Operation(summary = "Delegate task", description = "Delegate task to another user")
    public ResponseEntity<Void> delegateTask(
            @Parameter(description = "Task ID") @PathVariable String taskId,
            @Valid @RequestBody TaskDelegationRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        log.info("User {} delegating task {} to {}", userId, taskId, request.getToUserId());
        
        request.setTaskId(taskId);
        virtualGroupTaskService.delegateTask(userId, request);
        return ResponseEntity.ok().build();
    }
    
    // ==================== Lifecycle Management ====================
    
    @PostMapping("/{groupId}/activate")
    @Operation(summary = "Activate virtual group", description = "Activate virtual group")
    public ResponseEntity<VirtualGroupResult> activateGroup(
            @Parameter(description = "Virtual group ID") @PathVariable String groupId) {
        log.info("Activating virtual group: {}", groupId);
        VirtualGroupResult result = virtualGroupManager.activateGroup(groupId);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/{groupId}/deactivate")
    @Operation(summary = "Deactivate virtual group", description = "Deactivate virtual group")
    public ResponseEntity<VirtualGroupResult> deactivateGroup(
            @Parameter(description = "Virtual group ID") @PathVariable String groupId) {
        log.info("Deactivating virtual group: {}", groupId);
        VirtualGroupResult result = virtualGroupManager.deactivateGroup(groupId);
        return ResponseEntity.ok(result);
    }
