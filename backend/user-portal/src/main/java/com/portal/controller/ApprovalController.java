package com.portal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    
    // TODO: Inject ApproverService from admin-center via REST client
    
    @GetMapping("/pending")
    @Operation(summary = "Get pending approvals", description = "Get pending approval list for current approver")
    public ResponseEntity<List<Map<String, Object>>> getPendingApprovals(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Getting pending approvals for approver: {}", userId);
        
        // TODO: Call admin-center API to get pending requests for this approver
        // GET /api/v1/admin/permission-requests?approverId={userId}&status=PENDING
        
        return ResponseEntity.ok(List.of());
    }
    
    @PostMapping("/{requestId}/approve")
    @Operation(summary = "Approve request", description = "Approve a permission request")
    public ResponseEntity<Map<String, Object>> approveRequest(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Approver {} approving request: {}", userId, requestId);
        
        String comment = (String) request.get("comment");
        
        // TODO: Call admin-center API to approve request
        // POST /api/v1/admin/permission-requests/{requestId}/approve
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Request approved successfully"
        ));
    }
    
    @PostMapping("/{requestId}/reject")
    @Operation(summary = "Reject request", description = "Reject a permission request (comment required)")
    public ResponseEntity<Map<String, Object>> rejectRequest(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Approver {} rejecting request: {}", userId, requestId);
        
        String comment = (String) request.get("comment");
        if (comment == null || comment.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Comment is required when rejecting a request"
            ));
        }
        
        // TODO: Call admin-center API to reject request
        // POST /api/v1/admin/permission-requests/{requestId}/reject
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Request rejected successfully"
        ));
    }
    
    @GetMapping("/is-approver")
    @Operation(summary = "Check if user is approver", description = "Check if current user is an approver for any target")
    public ResponseEntity<Map<String, Object>> isApprover(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Checking if user {} is an approver", userId);
        
        // TODO: Call admin-center API to check if user is any approver
        // GET /api/v1/admin/approvers/check?userId={userId}
        
        return ResponseEntity.ok(Map.of(
            "isApprover", false,
            "virtualGroupCount", 0,
            "businessUnitCount", 0
        ));
    }
    
    @GetMapping("/history")
    @Operation(summary = "Get approval history", description = "Get approval history for current approver")
    public ResponseEntity<List<Map<String, Object>>> getApprovalHistory(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String status) {
        log.info("Getting approval history for approver: {}, status: {}", userId, status);
        
        // TODO: Call admin-center API to get approval history
        // GET /api/v1/admin/permission-requests?approverId={userId}&status={status}
        
        return ResponseEntity.ok(List.of());
    }
}
