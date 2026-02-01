package com.platform.security.model;

/**
 * User account status enumeration.
 * Validates: Requirements 1.4
 */
public enum UserStatus {
    /**
     * Active user - can login and use the system
     */
    ACTIVE,

    /**
     * Disabled by admin - cannot login (align with admin-center and DB CHECK)
     */
    DISABLED,

    /**
     * Inactive user - account is disabled, cannot login
     */
    INACTIVE,

    /**
     * Locked user - account is locked due to security reasons
     */
    LOCKED,

    /**
     * Pending activation - cannot login (align with admin-center and DB CHECK)
     */
    PENDING
}
