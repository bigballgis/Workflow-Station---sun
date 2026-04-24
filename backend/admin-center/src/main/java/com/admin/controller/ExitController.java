package com.admin.controller;

import com.admin.dto.response.BatchExitResult;
import com.admin.dto.response.ErrorResponse;
import com.admin.service.MemberManagementService;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Exit process controller for managing user exit operations.
 * Implements proper cleanup logic and resource management for exit processes.
 * Integrated with security validation and audit logging.
 * 
 * **Validates: Requirements 2.1, 2.4, 2.5, 4.2**
 */
@Slf4j
@RestController
@RequestMapping("/exit")
@RequiredArgsConstructor
@Tag(name = "退出流程", description = "用户退出虚拟组和业务单元的流程管理")
public class ExitController {
    
    private final MemberManagementService memberManagementService;
    private final SecurityIntegrationService securityIntegrationService;
    
    /**
     * Exit from a virtual group.
     * Implements proper cleanup logic including role revocation and audit logging.
     * 
     * @param virtualGroupId The virtual group ID to exit from
     * @param userId The user ID performing the exit
     * @return Success response
     */
    @PostMapping("/virtual-groups/{virtualGroupId}/users/{userId}")
    @Operation(
        summary = "退出虚拟组",
        description = "用户主动退出虚拟组，立即撤销继承的角色并记录审计日志"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "退出成功"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "请求参数无效",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "虚拟组或用户不存在",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "用户不是该虚拟组成员",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<Void> exitVirtualGroup(
            @Parameter(description = "虚拟组ID", required = true)
            @PathVariable String virtualGroupId,
            @Parameter(description = "用户ID", required = true)
            @PathVariable String userId) {
        
        // Validate and audit inputs for security
        securityIntegrationService.validateAndAuditInput("virtualGroupId", virtualGroupId, "exit_virtual_group");
        securityIntegrationService.validateAndAuditInput("userId", userId, "exit_virtual_group");
        
        log.info("Processing virtual group exit: virtualGroupId={}, userId={}", 
                virtualGroupId, userId);
        
        // Execute exit with proper cleanup logic
        memberManagementService.exitVirtualGroup(virtualGroupId, userId);
        
        log.info("Virtual group exit completed successfully: virtualGroupId={}, userId={}", 
                virtualGroupId, userId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Exit from a business unit.
     * Implements proper cleanup logic including membership removal and role deactivation.
     * 
     * @param businessUnitId The business unit ID to exit from
     * @param userId The user ID performing the exit
     * @return Success response
     */
    @PostMapping("/business-units/{businessUnitId}/users/{userId}")
    @Operation(
        summary = "退出业务单元",
        description = "用户主动退出业务单元，立即停用BU-Bounded角色并清理成员关系"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "退出成功"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "请求参数无效",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "业务单元或用户不存在",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "用户不是该业务单元成员",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<Void> exitBusinessUnit(
            @Parameter(description = "业务单元ID", required = true)
            @PathVariable String businessUnitId,
            @Parameter(description = "用户ID", required = true)
            @PathVariable String userId) {
        
        log.info("Processing business unit exit: businessUnitId={}, userId={}", 
                businessUnitId, userId);
        
        // Execute exit with proper cleanup logic
        memberManagementService.exitBusinessUnit(businessUnitId, userId);
        
        log.info("Business unit exit completed successfully: businessUnitId={}, userId={}", 
                businessUnitId, userId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Exit from specific business unit roles (legacy support).
     * Implements proper cleanup logic for role-specific exits.
     * 
     * @param businessUnitId The business unit ID
     * @param userId The user ID performing the exit
     * @param roleIds List of role IDs to exit from
     * @return Success response
     * @deprecated Use exitBusinessUnit for complete business unit exit
     */
    @PostMapping("/business-units/{businessUnitId}/users/{userId}/roles")
    @Operation(
        summary = "退出业务单元特定角色（已废弃）",
        description = "用户退出业务单元的特定角色，建议使用完整业务单元退出"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "退出成功"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "请求参数无效或角色列表为空",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "业务单元、用户或角色不存在",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "409",
            description = "用户没有指定的角色",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    @Deprecated
    public ResponseEntity<Void> exitBusinessUnitRoles(
            @Parameter(description = "业务单元ID", required = true)
            @PathVariable String businessUnitId,
            @Parameter(description = "用户ID", required = true)
            @PathVariable String userId,
            @Parameter(description = "要退出的角色ID列表", required = true)
            @RequestBody List<String> roleIds) {
        
        log.info("Processing business unit role exit: businessUnitId={}, userId={}, roleIds={}", 
                businessUnitId, userId, roleIds);
        
        // Validate input
        if (roleIds == null || roleIds.isEmpty()) {
            throw new IllegalArgumentException("Role list cannot be empty");
        }
        
        // Execute exit with proper cleanup logic
        memberManagementService.exitBusinessUnitRoles(businessUnitId, userId, roleIds);
        
        log.info("Business unit role exit completed successfully: businessUnitId={}, userId={}, roleIds={}", 
                businessUnitId, userId, roleIds);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Batch exit from multiple virtual groups.
     * Implements proper cleanup logic for bulk exit operations.
     * 
     * @param userId The user ID performing the exits
     * @param virtualGroupIds List of virtual group IDs to exit from
     * @return Success response
     */
    @PostMapping("/users/{userId}/virtual-groups/batch")
    @Operation(
        summary = "批量退出虚拟组",
        description = "用户批量退出多个虚拟组，确保所有退出操作的一致性"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "批量退出成功"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "请求参数无效或虚拟组列表为空",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "用户不存在",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "207",
            description = "部分退出成功，部分失败",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<BatchExitResult> batchExitVirtualGroups(
            @Parameter(description = "用户ID", required = true)
            @PathVariable String userId,
            @Parameter(description = "要退出的虚拟组ID列表", required = true)
            @RequestBody List<String> virtualGroupIds) {
        
        log.info("Processing batch virtual group exit: userId={}, virtualGroupIds={}", 
                userId, virtualGroupIds);
        
        if (virtualGroupIds == null || virtualGroupIds.isEmpty()) {
            throw new IllegalArgumentException("Virtual group list cannot be empty");
        }
        
        int successCount = 0;
        List<BatchExitResult.FailureDetail> failures = new ArrayList<>();
        
        for (String virtualGroupId : virtualGroupIds) {
            try {
                memberManagementService.exitVirtualGroup(virtualGroupId, userId);
                successCount++;
                log.debug("Successfully exited virtual group: {}", virtualGroupId);
            } catch (Exception e) {
                failures.add(BatchExitResult.FailureDetail.builder()
                        .targetId(virtualGroupId)
                        .errorMessage(e.getMessage())
                        .build());
                log.warn("Failed to exit virtual group {}: {}", virtualGroupId, e.getMessage());
            }
        }
        
        BatchExitResult result = BatchExitResult.builder()
                .totalCount(virtualGroupIds.size())
                .successCount(successCount)
                .failureCount(failures.size())
                .failures(failures)
                .build();
        
        log.info("Batch virtual group exit completed: userId={}, success={}, failures={}", 
                userId, successCount, failures.size());
        
        int status = failures.isEmpty() ? 200 : (successCount > 0 ? 207 : 400);
        return ResponseEntity.status(status).body(result);
    }
    
    /**
     * Batch exit from multiple business units.
     * Implements proper cleanup logic for bulk exit operations.
     * 
     * @param userId The user ID performing the exits
     * @param businessUnitIds List of business unit IDs to exit from
     * @return Success response
     */
    @PostMapping("/users/{userId}/business-units/batch")
    @Operation(
        summary = "批量退出业务单元",
        description = "用户批量退出多个业务单元，确保所有退出操作的一致性"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "批量退出成功"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "请求参数无效或业务单元列表为空",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "用户不存在",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "207",
            description = "部分退出成功，部分失败",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<BatchExitResult> batchExitBusinessUnits(
            @Parameter(description = "用户ID", required = true)
            @PathVariable String userId,
            @Parameter(description = "要退出的业务单元ID列表", required = true)
            @RequestBody List<String> businessUnitIds) {
        
        log.info("Processing batch business unit exit: userId={}, businessUnitIds={}", 
                userId, businessUnitIds);
        
        if (businessUnitIds == null || businessUnitIds.isEmpty()) {
            throw new IllegalArgumentException("Business unit list cannot be empty");
        }
        
        int successCount = 0;
        List<BatchExitResult.FailureDetail> failures = new ArrayList<>();
        
        for (String businessUnitId : businessUnitIds) {
            try {
                memberManagementService.exitBusinessUnit(businessUnitId, userId);
                successCount++;
                log.debug("Successfully exited business unit: {}", businessUnitId);
            } catch (Exception e) {
                failures.add(BatchExitResult.FailureDetail.builder()
                        .targetId(businessUnitId)
                        .errorMessage(e.getMessage())
                        .build());
                log.warn("Failed to exit business unit {}: {}", businessUnitId, e.getMessage());
            }
        }
        
        BatchExitResult result = BatchExitResult.builder()
                .totalCount(businessUnitIds.size())
                .successCount(successCount)
                .failureCount(failures.size())
                .failures(failures)
                .build();
        
        log.info("Batch business unit exit completed: userId={}, success={}, failures={}", 
                userId, successCount, failures.size());
        
        int status = failures.isEmpty() ? 200 : (successCount > 0 ? 207 : 400);
        return ResponseEntity.status(status).body(result);
    }
}