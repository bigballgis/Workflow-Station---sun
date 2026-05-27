package com.admin.component;

import com.admin.dto.request.BatchRoleMemberRequest;
import com.admin.dto.response.BatchRoleMemberResult;
import com.admin.entity.*;
import com.admin.enums.RoleType;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.*;
import com.admin.util.EntityTypeConverter;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.UserRole;
import com.platform.common.audit.Audited;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Role member management component
 * Manages role member addition, removal, batch operations, and change history
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleMemberManagerComponent {
    
    // DOS protection: batch operation user count limit
    private static final int MAX_BATCH_USERS = 500;

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionChangeHistoryRepository changeHistoryRepository;
    private final I18nService i18nService;
    
    /**
     * Assign role to user
     */
    @Transactional
    @Audited(action = "ROLE_ASSIGN", resourceType = "USER_ROLE", resourceId = "#userId")
    public void assignRoleToUser(String userId, String roleId, String assignedBy, String reason) {
        log.info("Assigning role {} to user {} by {}", roleId, userId, assignedBy);
        
        // Verify role exists
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        assertNotBuUnboundedDirectUserAssignment(role);

        // Check if already assigned
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            throw new AdminBusinessException("ROLE_ALREADY_ASSIGNED", "User already has this role");
        }
        
        // Create user-role association
        UserRole userRole = UserRole.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .roleId(roleId)
                .assignedAt(LocalDateTime.now())
                .assignedBy(assignedBy)
                .build();
        
        userRoleRepository.save(userRole);
        
        // Record change history
        recordChangeHistory("ROLE_ASSIGNED", userId, roleId, null, null, role.getName(), reason, assignedBy);
        
        log.info("Role {} assigned to user {} successfully", roleId, userId);
    }
    
    /**
     * Remove user role
     */
    @Transactional
    @Audited(action = "ROLE_REMOVE", resourceType = "USER_ROLE", resourceId = "#userId")
    public void removeRoleFromUser(String userId, String roleId, String removedBy, String reason) {
        log.info("Removing role {} from user {} by {}", roleId, userId, removedBy);
        
        UserRole userRole = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .orElseThrow(() -> new AdminBusinessException("ROLE_NOT_ASSIGNED", "User does not have this role"));
        
        // Fetch role to get name
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        String roleName = role.getName();
        
        userRoleRepository.delete(userRole);
        
        // Record change history
        recordChangeHistory("ROLE_REMOVED", userId, roleId, null, roleName, null, reason, removedBy);
        
        log.info("Role {} removed from user {} successfully", roleId, userId);
    }

    
    /**
     * Batch add role members
     */
    @Transactional
    public BatchRoleMemberResult batchAddMembers(BatchRoleMemberRequest request, String operatedBy) {
        log.info("Batch adding {} members to role {}", request.getUserIds().size(), request.getRoleId());
        
        // Verify role exists
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RoleNotFoundException(request.getRoleId()));
        assertNotBuUnboundedDirectUserAssignment(role);

        BatchRoleMemberResult result = BatchRoleMemberResult.builder()
                .total(request.getUserIds().size())
                .build();

        // DOS protection: limit batch operation user count
        if (request.getUserIds().size() > MAX_BATCH_USERS) {
            throw new AdminBusinessException("USER_COUNT_EXCEEDED",
                    i18nService.getMessage("admin.role.batch_user_count_exceeded", 
                            request.getUserIds().size(), MAX_BATCH_USERS));
        }

        for (String userId : request.getUserIds()) {
            try {
                // Verify user exists
                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    result.addFailure(userId, "USER_NOT_FOUND", i18nService.getMessage("admin.role.user_not_found"));
                    continue;
                }
                
                // Check if already assigned
                if (userRoleRepository.existsByUserIdAndRoleId(userId, request.getRoleId())) {
                    result.addFailure(userId, "ROLE_ALREADY_ASSIGNED", i18nService.getMessage("admin.role.user_already_has_role"));
                    continue;
                }
                
                // Create user-role association
                UserRole userRole = UserRole.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .roleId(request.getRoleId())
                        .assignedAt(LocalDateTime.now())
                        .assignedBy(operatedBy)
                        .build();
                
                userRoleRepository.save(userRole);
                
                // Record change history
                recordChangeHistory("ROLE_ASSIGNED", userId, request.getRoleId(), null, 
                        null, role.getName(), request.getReason(), operatedBy);
                
                result.addSuccess(userId);
                
            } catch (Exception e) {
                log.error("Failed to add member {} to role {}: {}", userId, request.getRoleId(), e.getMessage());
                result.addFailure(userId, "INTERNAL_ERROR", e.getMessage());
            }
        }
        
        log.info("Batch add members completed: {} success, {} failed", 
                result.getSuccessCount(), result.getFailureCount());
        
        return result;
    }
    
    /**
     * Batch remove role members
     */
    @Transactional
    public BatchRoleMemberResult batchRemoveMembers(BatchRoleMemberRequest request, String operatedBy) {
        log.info("Batch removing {} members from role {}", request.getUserIds().size(), request.getRoleId());
        
        // Verify role exists
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RoleNotFoundException(request.getRoleId()));
        
        BatchRoleMemberResult result = BatchRoleMemberResult.builder()
                .total(request.getUserIds().size())
                .build();

        // DOS protection: limit batch operation user count
        if (request.getUserIds().size() > MAX_BATCH_USERS) {
            throw new AdminBusinessException("USER_COUNT_EXCEEDED",
                    i18nService.getMessage("admin.role.batch_user_count_exceeded", 
                            request.getUserIds().size(), MAX_BATCH_USERS));
        }

        for (String userId : request.getUserIds()) {
            try {
                // Find user-role association
                UserRole userRole = userRoleRepository.findByUserIdAndRoleId(userId, request.getRoleId())
                        .orElse(null);
                
                if (userRole == null) {
                    result.addFailure(userId, "ROLE_NOT_ASSIGNED", i18nService.getMessage("admin.role.user_not_have_role"));
                    continue;
                }
                
                userRoleRepository.delete(userRole);
                
                // Record change history
                recordChangeHistory("ROLE_REMOVED", userId, request.getRoleId(), null, 
                        role.getName(), null, request.getReason(), operatedBy);
                
                result.addSuccess(userId);
                
            } catch (Exception e) {
                log.error("Failed to remove member {} from role {}: {}", userId, request.getRoleId(), e.getMessage());
                result.addFailure(userId, "INTERNAL_ERROR", e.getMessage());
            }
        }
        
        log.info("Batch remove members completed: {} success, {} failed", 
                result.getSuccessCount(), result.getFailureCount());
        
        return result;
    }
    
    /**
     * Get role member list
     */
    public List<UserRole> getRoleMembers(String roleId) {
        return userRoleRepository.findByRoleId(roleId);
    }
    
    /**
     * Get role members with pagination
     */
    public Page<UserRole> getRoleMembersPaged(String roleId, Pageable pageable) {
        return userRoleRepository.findByRoleIdPaged(roleId, pageable);
    }
    
    /**
     * Get user's role list
     */
    public List<Role> getUserRoles(String userId) {
        return roleRepository.findByUserId(userId);
    }
    
    /**
     * Get role member count
     */
    public long getRoleMemberCount(String roleId) {
        return userRoleRepository.countByRoleId(roleId);
    }

    
    /**
     * Get user's permission change history
     */
    public List<PermissionChangeHistory> getUserChangeHistory(String userId) {
        return changeHistoryRepository.findByTargetUserIdOrderByChangedAtDesc(userId);
    }
    
    /**
     * Get user's permission change history with pagination
     */
    public Page<PermissionChangeHistory> getUserChangeHistoryPaged(String userId, Pageable pageable) {
        return changeHistoryRepository.findByTargetUserId(userId, pageable);
    }
    
    /**
     * Get role change history
     */
    public List<PermissionChangeHistory> getRoleChangeHistory(String roleId) {
        return changeHistoryRepository.findByTargetRoleIdOrderByChangedAtDesc(roleId);
    }
    
    /**
     * Get role change history with pagination
     */
    public Page<PermissionChangeHistory> getRoleChangeHistoryPaged(String roleId, Pageable pageable) {
        return changeHistoryRepository.findByTargetRoleId(roleId, pageable);
    }
    
    /**
     * Get change history by time range
     */
    public List<PermissionChangeHistory> getChangeHistoryByTimeRange(Instant startTime, Instant endTime) {
        return changeHistoryRepository.findByTimeRange(startTime, endTime);
    }
    
    /**
     * BU_UNBOUNDED can only be obtained via virtual groups; direct assignment to sys_user_roles is forbidden.
     */
    private void assertNotBuUnboundedDirectUserAssignment(Role role) {
        if (EntityTypeConverter.toRoleType(role.getType()) == RoleType.BU_UNBOUNDED) {
            throw new AdminBusinessException(
                    "BU_UNBOUNDED_REQUIRES_VIRTUAL_GROUP",
                    i18nService.getMessage("admin.role.bu_unbounded_requires_virtual_group"));
        }
    }

    /**
     * Record permission change history
     */
    private void recordChangeHistory(String changeType, String userId, String roleId, 
                                     String permissionId, String oldValue, String newValue, 
                                     String reason, String changedBy) {
        PermissionChangeHistory history = PermissionChangeHistory.builder()
                .id(UUID.randomUUID().toString())
                .changeType(changeType)
                .targetUserId(userId)
                .targetRoleId(roleId)
                .targetPermissionId(permissionId)
                .oldValue(oldValue)
                .newValue(newValue)
                .reason(reason)
                .changedBy(changedBy)
                .changedAt(Instant.now())
                .build();
        
        changeHistoryRepository.save(history);
        
        log.debug("Permission change history recorded: type={}, userId={}, roleId={}", 
                changeType, userId, roleId);
    }
    
    /**
     * Check if user has the specified role
     */
    public boolean hasRole(String userId, String roleId) {
        return userRoleRepository.existsByUserIdAndRoleId(userId, roleId);
    }
    
    /**
     * Replace all roles for a user
     */
    @Transactional
    public void replaceUserRoles(String userId, List<String> newRoleIds, String operatedBy, String reason) {
        log.info("Replacing all roles for user {} with {} new roles", userId, newRoleIds.size());
        
        // Get current roles
        List<UserRole> currentRoles = userRoleRepository.findByUserId(userId);
        
        // Remove all current roles
        for (UserRole userRole : currentRoles) {
            // Fetch role to get name
            Role role = roleRepository.findById(userRole.getRoleId())
                    .orElse(null);
            String roleName = role != null ? role.getName() : "Unknown";
            recordChangeHistory("ROLE_REMOVED", userId, userRole.getRoleId(), null, 
                    roleName, null, reason, operatedBy);
        }
        userRoleRepository.deleteAll(currentRoles);
        
        // Add new roles
        for (String roleId : newRoleIds) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new RoleNotFoundException(roleId));
            assertNotBuUnboundedDirectUserAssignment(role);

            UserRole userRole = UserRole.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .roleId(roleId)
                    .assignedAt(LocalDateTime.now())
                    .assignedBy(operatedBy)
                    .build();
            
            userRoleRepository.save(userRole);
            
            recordChangeHistory("ROLE_ASSIGNED", userId, roleId, null, 
                    null, role.getName(), reason, operatedBy);
        }
        
        log.info("User {} roles replaced successfully", userId);
    }
}
