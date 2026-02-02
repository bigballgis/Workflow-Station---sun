package com.admin.helper;

import com.admin.enums.RoleType;
import com.admin.repository.RoleRepository;
import com.admin.util.EntityTypeConverter;
import com.platform.security.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper service for role-related business operations.
 * 
 * <p>This service provides utility methods for working with Role entities from platform-security,
 * including type checking, validation, and common role operations. It bridges the gap between
 * platform-security's String-based type fields and admin-center's type-safe enum-based business logic.</p>
 * 
 * <p><strong>Key Responsibilities:</strong></p>
 * <ul>
 *   <li>Role type checking (business roles, system roles, developer roles, admin roles)</li>
 *   <li>Type conversion between String and RoleType enum</li>
 *   <li>Role validation and filtering</li>
 *   <li>Common role queries</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * @Autowired
 * private RoleHelper roleHelper;
 * 
 * // Check if a role is a business role
 * if (roleHelper.isBusinessRole(role)) {
 *     // Handle business role logic
 * }
 * 
 * // Get all business roles
 * List<Role> businessRoles = roleHelper.getBusinessRoles();
 * 
 * // Get role type as enum
 * RoleType roleType = roleHelper.getRoleType(role);
 * }</pre>
 * 
 * @author Entity Architecture Alignment
 * @version 1.0
 * @see Role
 * @see RoleType
 * @see EntityTypeConverter
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleHelper {
    
    private final RoleRepository roleRepository;
    
    /**
     * Checks if a role type string represents a business role.
     * 
     * <p>Business roles are roles that are bound to business units, either bounded or unbounded.
     * These include:</p>
     * <ul>
     *   <li>BU_BOUNDED - Roles that are specific to a business unit</li>
     *   <li>BU_UNBOUNDED - Roles that span across business units</li>
     * </ul>
     * 
     * @param roleType the role type string from platform-security entity (e.g., "BU_BOUNDED", "BU_UNBOUNDED")
     * @return true if the role type is a business role, false otherwise
     * @throws IllegalArgumentException if the role type string is invalid
     */
    public boolean isBusinessRole(String roleType) {
        if (roleType == null) {
            return false;
        }
        
        return "BU_BOUNDED".equals(roleType) || "BU_UNBOUNDED".equals(roleType);
    }
    
    /**
     * Checks if a Role entity represents a business role.
     * 
     * <p>This is a convenience method that extracts the type from the Role entity
     * and delegates to {@link #isBusinessRole(String)}.</p>
     * 
     * @param role the Role entity to check
     * @return true if the role is a business role, false otherwise
     * @throws IllegalArgumentException if the role's type field is invalid
     */
    public boolean isBusinessRole(Role role) {
        if (role == null) {
            return false;
        }
        
        return isBusinessRole(role.getType());
    }
    
    /**
     * Checks if a Role entity is a system-defined role.
     * 
     * <p>System roles are predefined roles that cannot be deleted or modified by users.
     * They are typically created during system initialization and are marked with the
     * {@code isSystem} flag.</p>
     * 
     * @param role the Role entity to check
     * @return true if the role is a system role, false otherwise
     */
    public boolean isSystemRole(Role role) {
        if (role == null) {
            return false;
        }
        
        return Boolean.TRUE.equals(role.getIsSystem());
    }
    
    /**
     * Checks if a role type string represents a developer role.
     * 
     * <p>Developer roles are special roles assigned to developers for accessing
     * development tools and resources.</p>
     * 
     * @param roleType the role type string from platform-security entity
     * @return true if the role type is DEVELOPER, false otherwise
     */
    public boolean isDeveloperRole(String roleType) {
        if (roleType == null) {
            return false;
        }
        
        return "DEVELOPER".equals(roleType);
    }
    
    /**
     * Checks if a role type string represents an admin role.
     * 
     * <p>Admin roles have elevated privileges for system administration tasks.</p>
     * 
     * @param roleType the role type string from platform-security entity
     * @return true if the role type is ADMIN, false otherwise
     */
    public boolean isAdminRole(String roleType) {
        if (roleType == null) {
            return false;
        }
        
        return "ADMIN".equals(roleType);
    }
    
    /**
     * Gets the RoleType enum for a Role entity.
     * 
     * <p>This method converts the String type field from the platform-security Role entity
     * to the type-safe RoleType enum used in admin-center business logic.</p>
     * 
     * @param role the Role entity
     * @return the RoleType enum, or null if the role is null
     * @throws IllegalArgumentException if the role's type field contains an invalid value
     */
    public RoleType getRoleType(Role role) {
        if (role == null) {
            return null;
        }
        
        return EntityTypeConverter.toRoleType(role.getType());
    }
    
    /**
     * Gets all business roles from the repository.
     * 
     * <p>This method retrieves all roles that are either BU_BOUNDED or BU_UNBOUNDED.
     * The results include both active and inactive business roles.</p>
     * 
     * @return a list of all business roles, empty list if none found
     */
    public List<Role> getBusinessRoles() {
        log.debug("Fetching all business roles");
        
        List<Role> allRoles = roleRepository.findAll();
        return allRoles.stream()
                .filter(this::isBusinessRole)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all system roles from the repository.
     * 
     * <p>This method retrieves all roles that are marked as system roles (isSystem = true).
     * System roles are predefined and cannot be deleted or modified by users.</p>
     * 
     * @return a list of all system roles, empty list if none found
     */
    public List<Role> getSystemRoles() {
        log.debug("Fetching all system roles");
        
        List<Role> allRoles = roleRepository.findAll();
        return allRoles.stream()
                .filter(this::isSystemRole)
                .collect(Collectors.toList());
    }
    
    /**
     * Validates if a role type string is a valid RoleType.
     * 
     * <p>This method checks if the given string can be converted to a valid RoleType enum.
     * Valid values are: BU_BOUNDED, BU_UNBOUNDED, DEVELOPER, ADMIN.</p>
     * 
     * @param roleType the role type string to validate
     * @return true if the role type is valid, false if null or invalid
     */
    public boolean isValidRoleType(String roleType) {
        if (roleType == null) {
            return false;
        }
        
        try {
            EntityTypeConverter.toRoleType(roleType);
            return true;
        } catch (IllegalArgumentException e) {
            log.debug("Invalid role type: {}", roleType);
            return false;
        }
    }
}
