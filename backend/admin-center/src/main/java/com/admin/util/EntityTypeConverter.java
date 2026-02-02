package com.admin.util;

import com.admin.enums.BusinessUnitStatus;
import com.admin.enums.RoleType;
import com.admin.enums.VirtualGroupType;
import com.admin.enums.UserStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for converting between platform-security entity String types
 * and admin-center business logic Enum types.
 * 
 * <p>Platform-security entities use String types for flexibility and database compatibility,
 * while admin-center uses Enum types for type safety in business logic.</p>
 * 
 * <p>This converter handles bidirectional conversions and provides proper error handling
 * for invalid or unknown type values.</p>
 * 
 * @author Entity Architecture Alignment
 * @version 1.0
 */
@Slf4j
public class EntityTypeConverter {
    
    /**
     * Converts a String role type to RoleType enum.
     * 
     * <p>Supported conversions:</p>
     * <ul>
     *   <li>"BU_BOUNDED" → RoleType.BU_BOUNDED</li>
     *   <li>"BU_UNBOUNDED" → RoleType.BU_UNBOUNDED</li>
     *   <li>"ADMIN" → RoleType.ADMIN</li>
     *   <li>"DEVELOPER" → RoleType.DEVELOPER</li>
     * </ul>
     * 
     * @param typeStr the String role type from platform-security entity
     * @return the corresponding RoleType enum, or null if input is null
     * @throws IllegalArgumentException if the type string is not recognized
     */
    public static RoleType toRoleType(String typeStr) {
        if (typeStr == null) {
            return null;
        }
        
        return switch (typeStr) {
            case "BU_BOUNDED" -> RoleType.BU_BOUNDED;
            case "BU_UNBOUNDED" -> RoleType.BU_UNBOUNDED;
            case "DEVELOPER" -> RoleType.DEVELOPER;
            case "ADMIN" -> RoleType.ADMIN;
            default -> {
                log.error("Unknown role type: {}", typeStr);
                throw new IllegalArgumentException("Unknown role type: " + typeStr);
            }
        };
    }
    
    /**
     * Converts a RoleType enum to String for platform-security entity.
     * 
     * <p>Supported conversions:</p>
     * <ul>
     *   <li>RoleType.BU_BOUNDED → "BU_BOUNDED"</li>
     *   <li>RoleType.BU_UNBOUNDED → "BU_UNBOUNDED"</li>
     *   <li>RoleType.ADMIN → "ADMIN"</li>
     *   <li>RoleType.DEVELOPER → "DEVELOPER"</li>
     * </ul>
     * 
     * @param type the RoleType enum from admin-center business logic
     * @return the corresponding String type, or null if input is null
     * @throws IllegalArgumentException if the enum value is not recognized
     */
    public static String fromRoleType(RoleType type) {
        if (type == null) {
            return null;
        }
        
        return switch (type) {
            case BU_BOUNDED -> "BU_BOUNDED";
            case BU_UNBOUNDED -> "BU_UNBOUNDED";
            case DEVELOPER -> "DEVELOPER";
            case ADMIN -> "ADMIN";
            default -> {
                log.error("Unknown RoleType enum: {}", type);
                throw new IllegalArgumentException("Unknown RoleType enum: " + type);
            }
        };
    }
    
    /**
     * Converts a String virtual group type to VirtualGroupType enum.
     * 
     * <p>Supported conversions:</p>
     * <ul>
     *   <li>"SYSTEM" → VirtualGroupType.SYSTEM</li>
     *   <li>"CUSTOM" → VirtualGroupType.CUSTOM</li>
     * </ul>
     * 
     * @param typeStr the String virtual group type from platform-security entity
     * @return the corresponding VirtualGroupType enum, or null if input is null
     * @throws IllegalArgumentException if the type string is not recognized
     */
    public static VirtualGroupType toVirtualGroupType(String typeStr) {
        if (typeStr == null) {
            return null;
        }
        
        return switch (typeStr) {
            case "SYSTEM" -> VirtualGroupType.SYSTEM;
            case "CUSTOM" -> VirtualGroupType.CUSTOM;
            default -> {
                log.error("Unknown virtual group type: {}", typeStr);
                throw new IllegalArgumentException("Unknown virtual group type: " + typeStr);
            }
        };
    }
    
    /**
     * Converts a VirtualGroupType enum to String for platform-security entity.
     * 
     * <p>Supported conversions:</p>
     * <ul>
     *   <li>VirtualGroupType.SYSTEM → "SYSTEM"</li>
     *   <li>VirtualGroupType.CUSTOM → "CUSTOM"</li>
     * </ul>
     * 
     * @param type the VirtualGroupType enum from admin-center business logic
     * @return the corresponding String type, or null if input is null
     * @throws IllegalArgumentException if the enum value is not recognized
     */
    public static String fromVirtualGroupType(VirtualGroupType type) {
        if (type == null) {
            return null;
        }
        
        return switch (type) {
            case SYSTEM -> "SYSTEM";
            case CUSTOM -> "CUSTOM";
            default -> {
                log.error("Unknown VirtualGroupType enum: {}", type);
                throw new IllegalArgumentException("Unknown VirtualGroupType enum: " + type);
            }
        };
    }
    
    /**
     * Converts a String business unit status to BusinessUnitStatus enum.
     * 
     * <p>Supported conversions:</p>
     * <ul>
     *   <li>"ACTIVE" → BusinessUnitStatus.ACTIVE</li>
     *   <li>"DISABLED" → BusinessUnitStatus.DISABLED</li>
     * </ul>
     * 
     * @param statusStr the String business unit status from platform-security entity
     * @return the corresponding BusinessUnitStatus enum, or null if input is null
     * @throws IllegalArgumentException if the status string is not recognized
     */
    public static BusinessUnitStatus toBusinessUnitStatus(String statusStr) {
        if (statusStr == null) {
            return null;
        }
        
        return switch (statusStr) {
            case "ACTIVE" -> BusinessUnitStatus.ACTIVE;
            case "DISABLED" -> BusinessUnitStatus.DISABLED;
            default -> {
                log.error("Unknown business unit status: {}", statusStr);
                throw new IllegalArgumentException("Unknown business unit status: " + statusStr);
            }
        };
    }
    
    /**
     * Converts a BusinessUnitStatus enum to String for platform-security entity.
     * 
     * <p>Supported conversions:</p>
     * <ul>
     *   <li>BusinessUnitStatus.ACTIVE → "ACTIVE"</li>
     *   <li>BusinessUnitStatus.DISABLED → "DISABLED"</li>
     * </ul>
     * 
     * @param status the BusinessUnitStatus enum from admin-center business logic
     * @return the corresponding String status, or null if input is null
     * @throws IllegalArgumentException if the enum value is not recognized
     */
    public static String fromBusinessUnitStatus(BusinessUnitStatus status) {
        if (status == null) {
            return null;
        }
        
        return switch (status) {
            case ACTIVE -> "ACTIVE";
            case DISABLED -> "DISABLED";
            default -> {
                log.error("Unknown BusinessUnitStatus enum: {}", status);
                throw new IllegalArgumentException("Unknown BusinessUnitStatus enum: " + status);
            }
        };
    }
    
    /**
     * Converts a platform-security UserStatus enum to admin-center UserStatus enum.
     * 
     * <p>Supported conversions:</p>
     * <ul>
     *   <li>platform-security ACTIVE → admin-center ACTIVE</li>
     *   <li>platform-security INACTIVE → admin-center DISABLED</li>
     *   <li>platform-security LOCKED → admin-center LOCKED</li>
     * </ul>
     * 
     * <p><strong>Special Mapping:</strong> Platform-security uses INACTIVE while admin-center
     * uses DISABLED for the same semantic meaning (user account is disabled).</p>
     * 
     * @param platformStatus the UserStatus enum from platform-security entity
     * @return the corresponding admin-center UserStatus enum, or null if input is null
     * @throws IllegalArgumentException if the platform status is not recognized
     */
    public static UserStatus toUserStatus(com.platform.security.model.UserStatus platformStatus) {
        if (platformStatus == null) {
            return null;
        }
        
        return switch (platformStatus) {
            case ACTIVE -> UserStatus.ACTIVE;
            case INACTIVE -> UserStatus.DISABLED;  // Special mapping: INACTIVE → DISABLED
            case LOCKED -> UserStatus.LOCKED;
            default -> {
                log.error("Unknown platform-security UserStatus: {}", platformStatus);
                throw new IllegalArgumentException("Unknown platform-security UserStatus: " + platformStatus);
            }
        };
    }
    
    /**
     * Converts an admin-center UserStatus enum to platform-security UserStatus enum.
     * 
     * <p>Supported conversions:</p>
     * <ul>
     *   <li>admin-center ACTIVE → platform-security ACTIVE</li>
     *   <li>admin-center DISABLED → platform-security INACTIVE</li>
     *   <li>admin-center LOCKED → platform-security LOCKED</li>
     *   <li>admin-center PENDING → platform-security INACTIVE (pending users are treated as inactive)</li>
     * </ul>
     * 
     * <p><strong>Special Mappings:</strong></p>
     * <ul>
     *   <li>DISABLED → INACTIVE: Platform-security uses INACTIVE for disabled accounts</li>
     *   <li>PENDING → INACTIVE: Platform-security doesn't have a PENDING status, so pending
     *       users are mapped to INACTIVE until they are activated</li>
     * </ul>
     * 
     * @param adminStatus the UserStatus enum from admin-center business logic
     * @return the corresponding platform-security UserStatus enum, or null if input is null
     * @throws IllegalArgumentException if the admin status is not recognized
     */
    public static com.platform.security.model.UserStatus fromUserStatus(UserStatus adminStatus) {
        if (adminStatus == null) {
            return null;
        }
        
        return switch (adminStatus) {
            case ACTIVE -> com.platform.security.model.UserStatus.ACTIVE;
            case DISABLED -> com.platform.security.model.UserStatus.INACTIVE;  // Special mapping: DISABLED → INACTIVE
            case LOCKED -> com.platform.security.model.UserStatus.LOCKED;
            case PENDING -> com.platform.security.model.UserStatus.INACTIVE;  // Special mapping: PENDING → INACTIVE
            default -> {
                log.error("Unknown admin-center UserStatus: {}", adminStatus);
                throw new IllegalArgumentException("Unknown admin-center UserStatus: " + adminStatus);
            }
        };
    }
}
