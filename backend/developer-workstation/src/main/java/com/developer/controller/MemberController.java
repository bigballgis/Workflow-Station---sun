package com.developer.controller;

import com.developer.dto.ApiResponse;
import com.developer.dto.MemberRequest;
import com.developer.dto.MemberResponse;
import com.developer.dto.MemberUpdateRequest;
import com.developer.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Member Controller with full CRUD operations.
 * Handles member management operations with proper input validation and error handling.
 * 
 * Requirements: 2.1, 2.2, 2.5
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "Member Management", description = "Complete member management operations with CRUD functionality")
public class MemberController extends BaseController {
    
    private final MemberService memberService;
    
    @PostMapping
    @Operation(summary = "Create new member", description = "Create a new member with complete validation and business logic")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Member created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input or validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Member already exists"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<MemberResponse>> createMember(
            @Valid @RequestBody MemberRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String currentUserId) {
        
        log.info("Creating member with username: {} by user: {}", request.getUsername(), currentUserId);
        
        // Validate input fields for security using enhanced framework
        List<String> inputsToValidate = Arrays.asList(
            request.getUsername(),
            request.getFullName(),
            request.getEmail(),
            request.getEmployeeId(),
            request.getBusinessUnitId(),
            request.getBusinessUnitName(),
            request.getRole()
        );
        
        ResponseEntity<ApiResponse<MemberResponse>> result = handleRequestWithValidation(
                inputsToValidate, "member_create", () -> {
            // Sanitize inputs
            MemberRequest sanitizedRequest = MemberRequest.builder()
                    .username(sanitizeInput(request.getUsername()))
                    .fullName(sanitizeInput(request.getFullName()))
                    .email(sanitizeInput(request.getEmail()))
                    .employeeId(sanitizeInput(request.getEmployeeId()))
                    .businessUnitId(sanitizeInput(request.getBusinessUnitId()))
                    .businessUnitName(sanitizeInput(request.getBusinessUnitName()))
                    .role(sanitizeInput(request.getRole()))
                    .build();
            
            MemberResponse response = memberService.createMember(sanitizedRequest, sanitizeInput(currentUserId));
            
            // Log successful creation
            logSecurityEvent("MEMBER_CREATED", "Member created successfully", 
                    Map.of("username", sanitizedRequest.getUsername(), "createdBy", sanitizeInput(currentUserId)));
            
            return response;
        });
        
        // Convert to 201 Created status if successful
        if (result.getStatusCode().is2xxSuccessful() && result.getBody() != null && result.getBody().isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.getBody());
        }
        return result;
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get member by ID", description = "Retrieve a specific member by their unique identifier")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<MemberResponse>> getMember(
            @Parameter(description = "Member ID", required = true) @PathVariable Long id) {
        
        log.info("Retrieving member with ID: {}", id);
        
        return handleRequest(() -> memberService.getMember(id));
    }
    
    @GetMapping("/username/{username}")
    @Operation(summary = "Get member by username", description = "Retrieve a member by their username")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<MemberResponse>> getMemberByUsername(
            @Parameter(description = "Username", required = true) @PathVariable String username) {
        
        log.info("Retrieving member with username: {}", username);
        
        ResponseEntity<ApiResponse<MemberResponse>> result = handleRequestWithValidation(
                Arrays.asList(username), "member_get_by_username", () -> {
            String sanitizedUsername = sanitizeInput(username);
            
            // Log access attempt
            logSecurityEvent("MEMBER_ACCESS_BY_USERNAME", "Member access by username", 
                    Map.of("username", sanitizedUsername));
            
            return memberService.getMemberByUsername(sanitizedUsername)
                    .orElse(null);
        });
        
        // Convert to 404 if member not found
        if (result.getStatusCode().is2xxSuccessful() && result.getBody() != null && 
            result.getBody().isSuccess() && result.getBody().getData() == null) {
            return ResponseEntity.notFound().build();
        }
        return result;
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update member", description = "Update an existing member with partial or complete data")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input or validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflict with existing data"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<MemberResponse>> updateMember(
            @Parameter(description = "Member ID", required = true) @PathVariable Long id,
            @Valid @RequestBody MemberUpdateRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String currentUserId) {
        
        log.info("Updating member with ID: {} by user: {}", id, currentUserId);
        
        // Validate input fields for security using enhanced framework
        List<String> inputsToValidate = Arrays.asList(
            request.getFullName(),
            request.getEmail(),
            request.getEmployeeId(),
            request.getBusinessUnitId(),
            request.getBusinessUnitName(),
            request.getRole()
        );
        
        return handleRequestWithValidation(inputsToValidate, "member_update", () -> {
            // Sanitize inputs
            MemberUpdateRequest sanitizedRequest = MemberUpdateRequest.builder()
                    .fullName(sanitizeInput(request.getFullName()))
                    .email(sanitizeInput(request.getEmail()))
                    .employeeId(sanitizeInput(request.getEmployeeId()))
                    .businessUnitId(sanitizeInput(request.getBusinessUnitId()))
                    .businessUnitName(sanitizeInput(request.getBusinessUnitName()))
                    .role(sanitizeInput(request.getRole()))
                    .active(request.getActive())
                    .build();
            
            MemberResponse response = memberService.updateMember(id, sanitizedRequest, sanitizeInput(currentUserId));
            
            // Log successful update
            logSecurityEvent("MEMBER_UPDATED", "Member updated successfully", 
                    Map.of("memberId", id, "updatedBy", sanitizeInput(currentUserId)));
            
            return response;
        });
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete member", description = "Soft delete a member by setting active status to false")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Member deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<Void>> deleteMember(
            @Parameter(description = "Member ID", required = true) @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String currentUserId) {
        
        log.info("Deleting member with ID: {} by user: {}", id, currentUserId);
        
        ResponseEntity<ApiResponse<Void>> result = handleRequest(() -> {
            String sanitizedUserId = sanitizeInput(currentUserId);
            memberService.deleteMember(id, sanitizedUserId);
            
            // Log successful deletion
            logSecurityEvent("MEMBER_DELETED", "Member deleted successfully", 
                    Map.of("memberId", id, "deletedBy", sanitizedUserId));
            
            return null;
        });
        
        // Convert to 204 No Content if successful
        if (result.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.noContent().build();
        }
        return result;
    }
    
    @GetMapping
    @Operation(summary = "Get all active members", description = "Retrieve all active members with pagination and optional search")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Members retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<Page<MemberResponse>>> getAllMembers(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "fullName") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "asc") String sortDir,
            @Parameter(description = "Search term for name or username") @RequestParam(required = false) String search) {
        
        log.info("Retrieving members - page: {}, size: {}, sortBy: {}, sortDir: {}, search: {}", 
                page, size, sortBy, sortDir, search);
        
        return handleRequest(() -> {
            // Create pageable with sorting
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sanitizeInput(sortBy)));
            
            Page<MemberResponse> members;
            if (StringUtils.hasText(search)) {
                String sanitizedSearch = sanitizeInput(search);
                members = memberService.searchMembers(sanitizedSearch, pageable);
            } else {
                members = memberService.getAllActiveMembers(pageable);
            }
            
            return members;
        });
    }
    
    @GetMapping("/business-unit/{businessUnitId}")
    @Operation(summary = "Get members by business unit", description = "Retrieve all members belonging to a specific business unit")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Members retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembersByBusinessUnit(
            @Parameter(description = "Business Unit ID", required = true) @PathVariable String businessUnitId) {
        
        log.info("Retrieving members for business unit: {}", businessUnitId);
        
        return handleRequestWithValidation(businessUnitId, () -> {
            String sanitizedBusinessUnitId = sanitizeInput(businessUnitId);
            return memberService.getMembersByBusinessUnit(sanitizedBusinessUnitId);
        });
    }
}