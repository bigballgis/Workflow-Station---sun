package com.platform.security.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
     * Inactive user - account is disabled, cannot login
     */
    INACTIVE,
    
    /**
     * Locked user - account is locked due to security reasons
     */
    LOCKED;

    /**
     * Resolve status from string, with frontend alias support.
     * "DISABLED" and "PENDING" are frontend aliases for INACTIVE.
     */
    @JsonCreator
    public static UserStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toUpperCase()) {
            case "ACTIVE" -> ACTIVE;
            case "INACTIVE", "DISABLED", "PENDING" -> INACTIVE;
            case "LOCKED" -> LOCKED;
            default -> throw new IllegalArgumentException(
                "No enum constant " + UserStatus.class.getCanonicalName() + "." + value);
        };
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
