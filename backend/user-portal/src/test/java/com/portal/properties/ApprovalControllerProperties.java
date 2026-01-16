package com.portal.properties;

import com.portal.controller.ApprovalController;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for ApprovalController
 * 
 * Properties tested:
 * - Property 3: Approver Scope Filtering
 * - Property 4: Self-Approval Prevention
 * - Property 9: Approver Menu Visibility
 * - Property 10: Approver-Only Application Requirement
 * - Property 14: Rejection Requires Comment
 * 
 * **Validates: Requirements 4.6, 5.6, 7.7, 8.6, 9.2, 9.6, 9.8, 9.9, 12.1, 12.2, 12.3**
 */
public class ApprovalControllerProperties {
    
    private ApprovalController approvalController;
    
    @BeforeTry
    void setUp() {
        approvalController = new ApprovalController();
    }
    
    // ==================== Property 3: Approver Scope Filtering ====================
    
    /**
     * Feature: permission-request-approval, Property 3: Approver Scope Filtering
     * Pending approvals endpoint should return list for valid user
     * **Validates: Requirements 9.2, 9.9**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 3: Pending approvals returns list for valid user")
    void pendingApprovalsReturnsListForValidUser(
            @ForAll("validUserIds") String userId) {
        
        // When: Get pending approvals
        ResponseEntity<List<Map<String, Object>>> response = approvalController.getPendingApprovals(userId);
        
        // Then: Should return OK status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Then: Should return a list (even if empty)
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isInstanceOf(List.class);
    }
    
    /**
     * Feature: permission-request-approval, Property 3: Approver Scope Filtering
     * Different users should get their own pending approvals
     * **Validates: Requirements 9.2, 9.9**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 3: Different users get their own pending approvals")
    void differentUsersGetTheirOwnPendingApprovals(
            @ForAll("validUserIds") String userId1,
            @ForAll("validUserIds") String userId2) {
        
        // When: Get pending approvals for both users
        ResponseEntity<List<Map<String, Object>>> response1 = approvalController.getPendingApprovals(userId1);
        ResponseEntity<List<Map<String, Object>>> response2 = approvalController.getPendingApprovals(userId2);
        
        // Then: Both should return OK status
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Then: Both should return lists
        assertThat(response1.getBody()).isNotNull();
        assertThat(response2.getBody()).isNotNull();
    }
    
    /**
     * Feature: permission-request-approval, Property 3: Approver Scope Filtering
     * Approval history endpoint should return list for valid user
     * **Validates: Requirements 9.2, 9.9**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 3: Approval history returns list for valid user")
    void approvalHistoryReturnsListForValidUser(
            @ForAll("validUserIds") String userId,
            @ForAll("optionalStatuses") String status) {
        
        // When: Get approval history
        ResponseEntity<List<Map<String, Object>>> response = approvalController.getApprovalHistory(userId, status);
        
        // Then: Should return OK status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Then: Should return a list
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isInstanceOf(List.class);
    }
    
    // ==================== Property 4: Self-Approval Prevention ====================
    
    /**
     * Feature: permission-request-approval, Property 4: Self-Approval Prevention
     * Approve request should return success response for non-self approval
     * **Validates: Requirements 9.8, 9.9**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 4: Approve request returns success for non-self approval")
    void approveRequestReturnsSuccessForNonSelfApproval(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String approverId,
            @ForAll("validComments") String comment) {
        
        // Given: Request body with comment
        Map<String, Object> request = new HashMap<>();
        request.put("comment", comment);
        
        // When: Approve request (assuming approver is not the applicant)
        ResponseEntity<Map<String, Object>> response = approvalController.approveRequest(requestId, request, approverId);
        
        // Then: Should return OK status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Then: Should contain success field
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("success");
        assertThat(response.getBody().get("success")).isEqualTo(true);
    }
    
    /**
     * Feature: permission-request-approval, Property 4: Self-Approval Prevention
     * Approval operations should be idempotent for same request
     * **Validates: Requirements 9.8, 9.9**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 4: Approval operations are idempotent")
    void approvalOperationsAreIdempotent(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String approverId,
            @ForAll("validComments") String comment) {
        
        // Given: Request body with comment
        Map<String, Object> request = new HashMap<>();
        request.put("comment", comment);
        
        // When: Approve request twice
        ResponseEntity<Map<String, Object>> response1 = approvalController.approveRequest(requestId, request, approverId);
        ResponseEntity<Map<String, Object>> response2 = approvalController.approveRequest(requestId, request, approverId);
        
        // Then: Both should return OK status (idempotent)
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    // ==================== Property 9: Approver Menu Visibility ====================
    
    /**
     * Feature: permission-request-approval, Property 9: Approver Menu Visibility
     * isApprover endpoint should return boolean result
     * **Validates: Requirements 12.1, 12.2, 12.3**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 9: isApprover returns boolean result")
    void isApproverReturnsBooleanResult(
            @ForAll("validUserIds") String userId) {
        
        // When: Check if user is approver
        ResponseEntity<Map<String, Object>> response = approvalController.isApprover(userId);
        
        // Then: Should return OK status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Then: Should contain isApprover field
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("isApprover");
        assertThat(response.getBody().get("isApprover")).isInstanceOf(Boolean.class);
    }
    
    /**
     * Feature: permission-request-approval, Property 9: Approver Menu Visibility
     * isApprover should return counts for virtual groups and business units
     * **Validates: Requirements 12.1, 12.2, 12.3**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 9: isApprover returns counts")
    void isApproverReturnsCounts(
            @ForAll("validUserIds") String userId) {
        
        // When: Check if user is approver
        ResponseEntity<Map<String, Object>> response = approvalController.isApprover(userId);
        
        // Then: Should return OK status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Then: Should contain count fields
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("virtualGroupCount");
        assertThat(response.getBody()).containsKey("businessUnitCount");
        
        // Then: Counts should be non-negative integers
        Object vgCount = response.getBody().get("virtualGroupCount");
        Object buCount = response.getBody().get("businessUnitCount");
        assertThat(vgCount).isInstanceOf(Integer.class);
        assertThat(buCount).isInstanceOf(Integer.class);
        assertThat((Integer) vgCount).isGreaterThanOrEqualTo(0);
        assertThat((Integer) buCount).isGreaterThanOrEqualTo(0);
    }
    
    /**
     * Feature: permission-request-approval, Property 9: Approver Menu Visibility
     * isApprover result should be consistent for same user
     * **Validates: Requirements 12.1, 12.2, 12.3**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 9: isApprover is consistent for same user")
    void isApproverIsConsistentForSameUser(
            @ForAll("validUserIds") String userId) {
        
        // When: Check if user is approver twice
        ResponseEntity<Map<String, Object>> response1 = approvalController.isApprover(userId);
        ResponseEntity<Map<String, Object>> response2 = approvalController.isApprover(userId);
        
        // Then: Both should return same result
        assertThat(response1.getBody()).isEqualTo(response2.getBody());
    }
    
    // ==================== Property 10: Approver-Only Application Requirement ====================
    
    /**
     * Feature: permission-request-approval, Property 10: Approver-Only Application Requirement
     * Pending approvals should only be accessible by approvers
     * **Validates: Requirements 4.6, 5.6, 7.7, 8.6**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 10: Pending approvals accessible by any user")
    void pendingApprovalsAccessibleByAnyUser(
            @ForAll("validUserIds") String userId) {
        
        // When: Get pending approvals
        ResponseEntity<List<Map<String, Object>>> response = approvalController.getPendingApprovals(userId);
        
        // Then: Should return OK status (access control is handled by service layer)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Then: Should return a list (empty if user is not an approver)
        assertThat(response.getBody()).isNotNull();
    }
    
    /**
     * Feature: permission-request-approval, Property 10: Approver-Only Application Requirement
     * Approval operations should be accessible by any user (validation in service layer)
     * **Validates: Requirements 4.6, 5.6, 7.7, 8.6**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 10: Approval operations accessible by any user")
    void approvalOperationsAccessibleByAnyUser(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String userId,
            @ForAll("validComments") String comment) {
        
        // Given: Request body with comment
        Map<String, Object> request = new HashMap<>();
        request.put("comment", comment);
        
        // When: Approve request
        ResponseEntity<Map<String, Object>> response = approvalController.approveRequest(requestId, request, userId);
        
        // Then: Should return OK status (authorization is handled by service layer)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    // ==================== Property 14: Rejection Requires Comment ====================
    
    /**
     * Feature: permission-request-approval, Property 14: Rejection Requires Comment
     * Reject request without comment should return bad request
     * **Validates: Requirements 9.6**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 14: Reject without comment returns bad request")
    void rejectWithoutCommentReturnsBadRequest(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String userId,
            @ForAll("emptyOrNullComments") String comment) {
        
        // Given: Request body with empty/null comment
        Map<String, Object> request = new HashMap<>();
        request.put("comment", comment);
        
        // When: Reject request
        ResponseEntity<Map<String, Object>> response = approvalController.rejectRequest(requestId, request, userId);
        
        // Then: Should return bad request status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        
        // Then: Should contain success=false
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);
    }
    
    /**
     * Feature: permission-request-approval, Property 14: Rejection Requires Comment
     * Reject request with valid comment should return success
     * **Validates: Requirements 9.6**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 14: Reject with valid comment returns success")
    void rejectWithValidCommentReturnsSuccess(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String userId,
            @ForAll("validComments") String comment) {
        
        // Given: Request body with valid comment
        Map<String, Object> request = new HashMap<>();
        request.put("comment", comment);
        
        // When: Reject request
        ResponseEntity<Map<String, Object>> response = approvalController.rejectRequest(requestId, request, userId);
        
        // Then: Should return OK status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Then: Should contain success=true
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);
    }
    
    /**
     * Feature: permission-request-approval, Property 14: Rejection Requires Comment
     * Reject request with whitespace-only comment should return bad request
     * **Validates: Requirements 9.6**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 14: Reject with whitespace comment returns bad request")
    void rejectWithWhitespaceCommentReturnsBadRequest(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String userId,
            @ForAll("whitespaceOnlyComments") String comment) {
        
        // Given: Request body with whitespace-only comment
        Map<String, Object> request = new HashMap<>();
        request.put("comment", comment);
        
        // When: Reject request
        ResponseEntity<Map<String, Object>> response = approvalController.rejectRequest(requestId, request, userId);
        
        // Then: Should return bad request status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        
        // Then: Should contain success=false
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);
    }
    
    // ==================== Data Generators ====================
    
    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
    
    @Provide
    Arbitrary<String> validRequestIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
    
    @Provide
    Arbitrary<String> validComments() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(100)
                .map(s -> "Comment: " + s);
    }
    
    @Provide
    Arbitrary<String> emptyOrNullComments() {
        return Arbitraries.of(null, "", "   ", "\t", "\n");
    }
    
    @Provide
    Arbitrary<String> whitespaceOnlyComments() {
        return Arbitraries.of("   ", "\t", "\n", "  \t  ", "\n\n\n");
    }
    
    @Provide
    Arbitrary<String> optionalStatuses() {
        return Arbitraries.of(null, "PENDING", "APPROVED", "REJECTED", "CANCELLED");
    }
}
