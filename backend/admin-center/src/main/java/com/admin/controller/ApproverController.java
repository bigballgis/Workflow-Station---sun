package com.admin.controller;

import com.admin.dto.request.ApproverAddRequest;
import com.admin.dto.response.ApproverInfo;
import com.admin.entity.Approver;
import com.admin.entity.User;
import com.admin.enums.ApproverTargetType;
import com.admin.service.ApproverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 审批人配置控制器
 */
@RestController
@RequestMapping("/approvers")
@RequiredArgsConstructor
@Tag(name = "审批人配置", description = "虚拟组和业务单元的审批人配置管理")
public class ApproverController {
    
    private final ApproverService approverService;
    
    @PostMapping
    @Operation(summary = "添加审批人")
    public ResponseEntity<Void> addApprover(@RequestBody ApproverAddRequest request) {
        approverService.addApprover(request.getTargetType(), request.getTargetId(), request.getUserId());
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{approverId}")
    @Operation(summary = "移除审批人")
    public ResponseEntity<Void> removeApprover(@PathVariable String approverId) {
        approverService.removeApprover(approverId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/virtual-groups/{groupId}")
    @Operation(summary = "获取虚拟组的审批人列表")
    public ResponseEntity<List<ApproverInfo>> getVirtualGroupApprovers(@PathVariable String groupId) {
        List<Approver> approvers = approverService.getApproverConfigs(ApproverTargetType.VIRTUAL_GROUP, groupId);
        List<ApproverInfo> result = approvers.stream()
                .map(ApproverInfo::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/business-units/{unitId}")
    @Operation(summary = "获取业务单元的审批人列表")
    public ResponseEntity<List<ApproverInfo>> getBusinessUnitApprovers(@PathVariable String unitId) {
        List<Approver> approvers = approverService.getApproverConfigs(ApproverTargetType.BUSINESS_UNIT, unitId);
        List<ApproverInfo> result = approvers.stream()
                .map(ApproverInfo::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/virtual-groups/{groupId}/users/{userId}")
    @Operation(summary = "移除虚拟组的审批人")
    public ResponseEntity<Void> removeVirtualGroupApprover(
            @PathVariable String groupId,
            @PathVariable String userId) {
        approverService.removeApproverByTarget(ApproverTargetType.VIRTUAL_GROUP, groupId, userId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/business-units/{unitId}/users/{userId}")
    @Operation(summary = "移除业务单元的审批人")
    public ResponseEntity<Void> removeBusinessUnitApprover(
            @PathVariable String unitId,
            @PathVariable String userId) {
        approverService.removeApproverByTarget(ApproverTargetType.BUSINESS_UNIT, unitId, userId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/check")
    @Operation(summary = "检查用户是否是指定目标的审批人")
    public ResponseEntity<Boolean> checkIsApprover(
            @RequestParam String userId,
            @RequestParam ApproverTargetType targetType,
            @RequestParam String targetId) {
        boolean isApprover = approverService.isApprover(userId, targetType, targetId);
        return ResponseEntity.ok(isApprover);
    }
    
    @GetMapping("/user/{userId}/virtual-groups")
    @Operation(summary = "获取用户作为审批人的所有虚拟组ID")
    public ResponseEntity<List<String>> getUserApproverVirtualGroups(@PathVariable String userId) {
        List<String> groupIds = approverService.getApproverVirtualGroupIds(userId);
        return ResponseEntity.ok(groupIds);
    }
    
    @GetMapping("/user/{userId}/business-units")
    @Operation(summary = "获取用户作为审批人的所有业务单元ID")
    public ResponseEntity<List<String>> getUserApproverBusinessUnits(@PathVariable String userId) {
        List<String> unitIds = approverService.getApproverBusinessUnitIds(userId);
        return ResponseEntity.ok(unitIds);
    }
    
    @GetMapping("/user/{userId}/is-any")
    @Operation(summary = "检查用户是否是任何目标的审批人")
    public ResponseEntity<Boolean> checkIsAnyApprover(@PathVariable String userId) {
        boolean isApprover = approverService.isAnyApprover(userId);
        return ResponseEntity.ok(isApprover);
    }
}
