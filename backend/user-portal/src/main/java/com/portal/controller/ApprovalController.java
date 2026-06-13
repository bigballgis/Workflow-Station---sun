package com.portal.controller;

import com.portal.client.AdminCenterClient;
import com.platform.common.dto.ApiResponse;
import com.portal.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Approval Controller
 * Handles approval operations for permission requests
 */
@Slf4j
@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Permission request approval operations")
public class ApprovalController {

    private final AdminCenterClient adminCenterClient;
    
    @GetMapping("/pending")
    @Operation(summary = "Get pending approvals", description = "Get pending approval list for current approver")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPendingApprovals(
            @CurrentUserId String userId) {
        log.info("Getting pending approvals for approver: {}", userId);

        return adminCenterClient.getPendingApprovals(userId)
                .<ResponseEntity<ApiResponse<List<Map<String, Object>>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }

    @PostMapping("/{requestId}/approve")
    @Operation(summary = "Approve request", description = "Approve a permission request")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveRequest(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> request,
            @CurrentUserId String userId) {
        log.info("Approver {} approving request: {}", userId, requestId);
        
        String comment = (String) request.get("comment");

        return adminCenterClient.approveRequest(requestId, userId, comment)
                .<ResponseEntity<ApiResponse<Map<String, Object>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
    
    @PostMapping("/{requestId}/reject")
    @Operation(summary = "Reject request", description = "Reject a permission request (comment required)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rejectRequest(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> request,
            @CurrentUserId String userId) {
        log.info("Approver {} rejecting request: {}", userId, requestId);
        
        String comment = (String) request.get("comment");
        if (comment == null || comment.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("400", "Comment is required when rejecting a request"));
        }

        return adminCenterClient.rejectRequest(requestId, userId, comment)
                .<ResponseEntity<ApiResponse<Map<String, Object>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
    
    @GetMapping("/is-approver")
    @Operation(summary = "Check if user is approver", description = "Check if current user is an approver for any target")
    public ResponseEntity<ApiResponse<Map<String, Object>>> isApprover(
            @CurrentUserId String userId) {
        log.info("Checking if user {} is an approver", userId);

        return adminCenterClient.checkIsApprover(userId)
                .<ResponseEntity<ApiResponse<Map<String, Object>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
    
    @GetMapping("/history")
    @Operation(summary = "Get approval history", description = "Get approval history for current approver")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getApprovalHistory(
            @CurrentUserId String userId,
            @RequestParam(required = false) String status) {
        log.info("Getting approval history for approver: {}, status: {}", userId, status);

        return adminCenterClient.getApprovalHistory(userId, status)
                .<ResponseEntity<ApiResponse<List<Map<String, Object>>>>>map(data -> ResponseEntity.ok(ApiResponse.success(data)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error("503", "Admin center service unavailable")));
    }
}
