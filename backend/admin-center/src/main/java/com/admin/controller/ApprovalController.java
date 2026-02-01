package com.admin.controller;

import com.admin.dto.request.ApprovalRequest;
import com.admin.dto.response.ErrorResponse;
import com.admin.dto.response.PermissionRequestInfo;
import com.admin.entity.PermissionRequest;
import com.admin.service.PermissionRequestService;
import com.platform.common.security.SecurityIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * Approval workflow controller for managing permission request approvals.
 * Implements approval workflow state management with proper business logic.
 * Integrated with security validation and audit logging.
 * 
 * **Validates: Requirements 2.1, 2.3, 2.5, 4.2**
 */
@Slf4j
@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
@Tag(name = "审批工作流", description = "权限申请审批工作流管理")
public class ApprovalController {
    
    private final PermissionRequestService permissionRequestService;
    private final SecurityIntegrationService securityIntegrationService;
    
    /**
     * Approve a permission request.
     * Implements proper approval workflow state management.
     * 
     * @param requestId The permission request ID to approve
     * @param request The approval request containing approver ID and comment
     * @return Updated permission request information
     */
    @PostMapping("/{requestId}/approve")
    @Operation(
        summary = "审批通过权限申请",
        description = "审批人审批通过指定的权限申请，立即生效并更新状态"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "审批成功",
            content = @Content(schema = @Schema(implementation = PermissionRequestInfo.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "请求参数无效",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "无权限进行审批",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "权限申请不存在",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<PermissionRequestInfo> approveRequest(
            @Parameter(description = "权限申请ID", required = true)
            @PathVariable String requestId,
            @Parameter(description = "审批请求信息", required = true)
            @Valid @RequestBody ApprovalRequest request) {
        
        // Validate and audit inputs for security
        securityIntegrationService.validateAndAuditInput("requestId", requestId, "approval_approve");
        securityIntegrationService.validateAndAuditInput("approverId", request.getApproverId(), "approval_approve");
        if (request.getComment() != null) {
            securityIntegrationService.validateAndAuditInput("comment", request.getComment(), "approval_approve");
        }
        
        log.info("Processing approval request: requestId={}, approverId={}", 
                requestId, request.getApproverId());
        
        // Execute approval with proper state management
        permissionRequestService.approve(requestId, request.getApproverId(), request.getComment());
        
        // Return updated request information
        PermissionRequest updatedRequest = permissionRequestService.getRequestDetail(requestId);
        PermissionRequestInfo response = PermissionRequestInfo.fromEntity(updatedRequest);
        
        log.info("Approval completed successfully: requestId={}, status={}", 
                requestId, updatedRequest.getStatus());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reject a permission request.
     * Implements proper rejection workflow state management.
     * 
     * @param requestId The permission request ID to reject
     * @param request The rejection request containing approver ID and comment
     * @return Updated permission request information
     */
    @PostMapping("/{requestId}/reject")
    @Operation(
        summary = "拒绝权限申请",
        description = "审批人拒绝指定的权限申请，更新状态并记录拒绝原因"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "拒绝成功",
            content = @Content(schema = @Schema(implementation = PermissionRequestInfo.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "请求参数无效或缺少拒绝理由",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "无权限进行审批",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "权限申请不存在",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<PermissionRequestInfo> rejectRequest(
            @Parameter(description = "权限申请ID", required = true)
            @PathVariable String requestId,
            @Parameter(description = "拒绝请求信息", required = true)
            @Valid @RequestBody ApprovalRequest request) {
        
        log.info("Processing rejection request: requestId={}, approverId={}", 
                requestId, request.getApproverId());
        
        // Execute rejection with proper state management
        permissionRequestService.reject(requestId, request.getApproverId(), request.getComment());
        
        // Return updated request information
        PermissionRequest updatedRequest = permissionRequestService.getRequestDetail(requestId);
        PermissionRequestInfo response = PermissionRequestInfo.fromEntity(updatedRequest);
        
        log.info("Rejection completed successfully: requestId={}, status={}", 
                requestId, updatedRequest.getStatus());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get approval workflow status for a permission request.
     * Provides current state and available transitions.
     * 
     * @param requestId The permission request ID
     * @return Current permission request information with workflow state
     */
    @GetMapping("/{requestId}/status")
    @Operation(
        summary = "获取审批工作流状态",
        description = "获取权限申请的当前审批状态和工作流信息"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "获取成功",
            content = @Content(schema = @Schema(implementation = PermissionRequestInfo.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "权限申请不存在",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<PermissionRequestInfo> getApprovalStatus(
            @Parameter(description = "权限申请ID", required = true)
            @PathVariable String requestId) {
        
        log.debug("Getting approval status for request: {}", requestId);
        
        PermissionRequest request = permissionRequestService.getRequestDetail(requestId);
        PermissionRequestInfo response = PermissionRequestInfo.fromEntity(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get pending approval requests for a specific approver.
     * Supports workflow management by showing approver's queue.
     * 
     * @param approverId The approver user ID
     * @return List of pending permission requests for the approver
     */
    @GetMapping("/pending/{approverId}")
    @Operation(
        summary = "获取待审批申请列表",
        description = "获取指定审批人的待审批权限申请列表"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "获取成功",
            content = @Content(schema = @Schema(implementation = PermissionRequestInfo.class))
        )
    })
    public ResponseEntity<java.util.List<PermissionRequestInfo>> getPendingApprovals(
            @Parameter(description = "审批人ID", required = true)
            @PathVariable String approverId) {
        
        log.debug("Getting pending approvals for approver: {}", approverId);
        
        java.util.List<PermissionRequest> pendingRequests = 
                permissionRequestService.getPendingRequestsForApprover(approverId);
        
        java.util.List<PermissionRequestInfo> response = pendingRequests.stream()
                .map(PermissionRequestInfo::fromEntity)
                .toList();
        
        return ResponseEntity.ok(response);
    }
}