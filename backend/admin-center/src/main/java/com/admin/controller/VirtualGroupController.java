package com.admin.controller;

import com.admin.component.VirtualGroupManagerComponent;
import com.admin.dto.request.TaskClaimRequest;
import com.admin.dto.request.TaskDelegationRequest;
import com.admin.dto.request.VirtualGroupCreateRequest;
import com.admin.dto.request.VirtualGroupMemberRequest;
import com.admin.dto.response.*;
import com.admin.service.VirtualGroupTaskService;
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

/**
 * 虚拟组管理 RESTful API
 */
@Slf4j
@RestController
@RequestMapping("/virtual-groups")
@RequiredArgsConstructor
@Tag(name = "虚拟组管理", description = "虚拟组的创建、成员管理和任务查询接口")
public class VirtualGroupController {
    
    private final VirtualGroupManagerComponent virtualGroupManager;
    private final VirtualGroupTaskService virtualGroupTaskService;
    
    // ==================== 虚拟组 CRUD ====================
    
    @PostMapping
    @Operation(summary = "创建虚拟组", description = "创建新的虚拟组")
    public ResponseEntity<VirtualGroupResult> createVirtualGroup(
            @Valid @RequestBody VirtualGroupCreateRequest request) {
        log.info("Creating virtual group: {}", request.getName());
        VirtualGroupResult result = virtualGroupManager.createVirtualGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
    
    @GetMapping
    @Operation(summary = "获取虚拟组列表", description = "获取所有虚拟组列表")
    public ResponseEntity<List<VirtualGroupInfo>> listVirtualGroups(
            @Parameter(description = "虚拟组类型") @RequestParam(required = false) String type,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        log.info("Listing virtual groups, type: {}, status: {}", type, status);
        List<VirtualGroupInfo> groups = virtualGroupManager.listVirtualGroups(type, status);
        return ResponseEntity.ok(groups);
    }
    
    @GetMapping("/{groupId}")
    @Operation(summary = "获取虚拟组详情", description = "根据ID获取虚拟组详细信息")
    public ResponseEntity<VirtualGroupInfo> getVirtualGroup(
            @Parameter(description = "虚拟组ID") @PathVariable String groupId) {
        log.info("Getting virtual group: {}", groupId);
        VirtualGroupInfo group = virtualGroupManager.getVirtualGroup(groupId);
        return ResponseEntity.ok(group);
    }
    
    @PutMapping("/{groupId}")
    @Operation(summary = "更新虚拟组", description = "更新虚拟组信息")
    public ResponseEntity<VirtualGroupResult> updateVirtualGroup(
            @Parameter(description = "虚拟组ID") @PathVariable String groupId,
            @Valid @RequestBody VirtualGroupCreateRequest request) {
        log.info("Updating virtual group: {}", groupId);
        VirtualGroupResult result = virtualGroupManager.updateVirtualGroup(groupId, request);
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{groupId}")
    @Operation(summary = "删除虚拟组", description = "删除虚拟组")
    public ResponseEntity<Void> deleteVirtualGroup(
            @Parameter(description = "虚拟组ID") @PathVariable String groupId) {
        log.info("Deleting virtual group: {}", groupId);
        virtualGroupManager.deleteVirtualGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    
    // ==================== 成员管理 ====================
    
    @GetMapping("/{groupId}/members")
    @Operation(summary = "获取虚拟组成员", description = "获取虚拟组的所有成员")
    public ResponseEntity<List<VirtualGroupMemberInfo>> getGroupMembers(
            @Parameter(description = "虚拟组ID") @PathVariable String groupId) {
        log.info("Getting members of virtual group: {}", groupId);
        List<VirtualGroupMemberInfo> members = virtualGroupManager.getGroupMembers(groupId);
        return ResponseEntity.ok(members);
    }
    
    @PostMapping("/{groupId}/members")
    @Operation(summary = "添加成员", description = "向虚拟组添加成员")
    public ResponseEntity<VirtualGroupResult> addMember(
            @Parameter(description = "虚拟组ID") @PathVariable String groupId,
            @Valid @RequestBody VirtualGroupMemberRequest request) {
        log.info("Adding member {} to virtual group: {}", request.getUserId(), groupId);
        VirtualGroupResult result = virtualGroupManager.addMember(groupId, request);
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{groupId}/members/{userId}")
    @Operation(summary = "移除成员", description = "从虚拟组移除成员")
    public ResponseEntity<VirtualGroupResult> removeMember(
            @Parameter(description = "虚拟组ID") @PathVariable String groupId,
            @Parameter(description = "用户ID") @PathVariable String userId) {
        log.info("Removing member {} from virtual group: {}", userId, groupId);
        VirtualGroupResult result = virtualGroupManager.removeMember(groupId, userId);
        return ResponseEntity.ok(result);
    }
    
    // ==================== 任务查询 ====================
    
    @GetMapping("/{groupId}/tasks")
    @Operation(summary = "获取虚拟组任务", description = "获取分配给虚拟组的任务列表")
    public ResponseEntity<List<GroupTaskInfo>> getGroupTasks(
            @Parameter(description = "虚拟组ID") @PathVariable String groupId,
            @Parameter(description = "当前用户ID") @RequestHeader("X-User-Id") String userId) {
        log.info("Getting tasks for virtual group: {} by user: {}", groupId, userId);
        List<GroupTaskInfo> tasks = virtualGroupTaskService.getGroupTasks(groupId, userId);
        return ResponseEntity.ok(tasks);
    }
    
    @GetMapping("/my-tasks")
    @Operation(summary = "获取用户可见的组任务", description = "获取当前用户可见的所有虚拟组任务")
    public ResponseEntity<List<GroupTaskInfo>> getUserVisibleGroupTasks(
            @Parameter(description = "当前用户ID") @RequestHeader("X-User-Id") String userId) {
        log.info("Getting visible group tasks for user: {}", userId);
        List<GroupTaskInfo> tasks = virtualGroupTaskService.getUserVisibleGroupTasks(userId);
        return ResponseEntity.ok(tasks);
    }
    
    @GetMapping("/{groupId}/tasks/{taskId}/history")
    @Operation(summary = "获取任务历史", description = "获取任务的处理历史")
    public ResponseEntity<List<TaskHistoryInfo>> getTaskHistory(
            @Parameter(description = "虚拟组ID") @PathVariable String groupId,
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        log.info("Getting history for task: {} in group: {}", taskId, groupId);
        List<TaskHistoryInfo> history = virtualGroupTaskService.getTaskHistory(taskId);
        return ResponseEntity.ok(history);
    }
    
    // ==================== 任务操作 ====================
    
    @PostMapping("/{groupId}/tasks/{taskId}/claim")
    @Operation(summary = "认领任务", description = "认领虚拟组任务")
    public ResponseEntity<Void> claimTask(
            @Parameter(description = "虚拟组ID") @PathVariable String groupId,
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "当前用户ID") @RequestHeader("X-User-Id") String userId,
            @RequestBody(required = false) TaskClaimRequest request) {
        log.info("User {} claiming task {} from group {}", userId, taskId, groupId);
        
        TaskClaimRequest claimRequest = request != null ? request : new TaskClaimRequest();
        claimRequest.setTaskId(taskId);
        claimRequest.setGroupId(groupId);
        
        virtualGroupTaskService.claimTask(userId, claimRequest);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/tasks/{taskId}/delegate")
    @Operation(summary = "委托任务", description = "将任务委托给其他用户")
    public ResponseEntity<Void> delegateTask(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "当前用户ID") @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody TaskDelegationRequest request) {
        log.info("User {} delegating task {} to {}", userId, taskId, request.getToUserId());
        
        request.setTaskId(taskId);
        virtualGroupTaskService.delegateTask(userId, request);
        return ResponseEntity.ok().build();
    }
    
    // ==================== 生命周期管理 ====================
    
    @PostMapping("/{groupId}/activate")
    @Operation(summary = "激活虚拟组", description = "激活虚拟组")
    public ResponseEntity<VirtualGroupResult> activateGroup(
            @Parameter(description = "虚拟组ID") @PathVariable String groupId) {
        log.info("Activating virtual group: {}", groupId);
        VirtualGroupResult result = virtualGroupManager.activateGroup(groupId);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/{groupId}/deactivate")
    @Operation(summary = "停用虚拟组", description = "停用虚拟组")
    public ResponseEntity<VirtualGroupResult> deactivateGroup(
            @Parameter(description = "虚拟组ID") @PathVariable String groupId) {
        log.info("Deactivating virtual group: {}", groupId);
        VirtualGroupResult result = virtualGroupManager.deactivateGroup(groupId);
        return ResponseEntity.ok(result);
    }
}
