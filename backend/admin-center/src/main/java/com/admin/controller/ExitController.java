package com.admin.controller;

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
            throw new IllegalArgumentException("角色列表不能为空");
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
    public ResponseEntity<Void> batchExitVirtualGroups(
            @Parameter(description = "用户ID", required = true)
            @PathVariable String userId,
            @Parameter(description = "要退出的虚拟组ID列表", required = true)
            @RequestBody List<String> virtualGroupIds) {
        
        log.info("Processing batch virtual group exit: userId={}, virtualGroupIds={}", 
                userId, virtualGroupIds);
        
        // Validate input
        if (virtualGroupIds == null || virtualGroupIds.isEmpty()) {
            throw new IllegalArgumentException("虚拟组列表不能为空");
        }
        
        // Execute batch exit with proper cleanup logic
        int successCount = 0;
        int failureCount = 0;
        
        for (String virtualGroupId : virtualGroupIds) {
            try {
                memberManagementService.exitVirtualGroup(virtualGroupId, userId);
                successCount++;
                log.debug("Successfully exited virtual group: {}", virtualGroupId);
            } catch (Exception e) {
                failureCount++;
                log.warn("Failed to exit virtual group {}: {}", virtualGroupId, e.getMessage());
            }
        }
        
        log.info("Batch virtual group exit completed: userId={}, success={}, failures={}", 
                userId, successCount, failureCount);
        
        if (failureCount > 0 && successCount > 0) {
            // Partial success - return 207 Multi-Status
            return ResponseEntity.status(207).build();
        } else if (failureCount > 0) {
            // All failed - this would be handled by exception handler
            throw new RuntimeException("所有虚拟组退出操作均失败");
        }
        
        return ResponseEntity.ok().build();
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
    public ResponseEntity<Void> batchExitBusinessUnits(
            @Parameter(description = "用户ID", required = true)
            @PathVariable String userId,
            @Parameter(description = "要退出的业务单元ID列表", required = true)
            @RequestBody List<String> businessUnitIds) {
        
        log.info("Processing batch business unit exit: userId={}, businessUnitIds={}", 
                userId, businessUnitIds);
        
        // Validate input
        if (businessUnitIds == null || businessUnitIds.isEmpty()) {
            throw new IllegalArgumentException("业务单元列表不能为空");
        }
        
        // Execute batch exit with proper cleanup logic
        int successCount = 0;
        int failureCount = 0;
        
        for (String businessUnitId : businessUnitIds) {
            try {
                memberManagementService.exitBusinessUnit(businessUnitId, userId);
                successCount++;
                log.debug("Successfully exited business unit: {}", businessUnitId);
            } catch (Exception e) {
                failureCount++;
                log.warn("Failed to exit business unit {}: {}", businessUnitId, e.getMessage());
            }
        }
        
        log.info("Batch business unit exit completed: userId={}, success={}, failures={}", 
                userId, successCount, failureCount);
        
        if (failureCount > 0 && successCount > 0) {
            // Partial success - return 207 Multi-Status
            return ResponseEntity.status(207).build();
        } else if (failureCount > 0) {
            // All failed - this would be handled by exception handler
            throw new RuntimeException("所有业务单元退出操作均失败");
        }
        
        return ResponseEntity.ok().build();
    }
}