package com.platform.security.exception;

/**
 * Authentication error codes.
 * Validates: Requirements 2.2, 2.3, 2.4, 4.2, 4.3
 */
public enum AuthErrorCode {
    
    AUTH_001("AUTH_001", "Invalid username or password", 401),
    AUTH_002("AUTH_002", "Account is locked", 403),
    AUTH_003("AUTH_003", "Account is not active", 403),
    AUTH_004("AUTH_004", "Token has expired", 401),
    AUTH_005("AUTH_005", "Invalid token", 401),
    AUTH_006("AUTH_006", "Token has been revoked", 401),
    AUTH_007("AUTH_007", "Refresh token has expired", 401),
    AUTH_008("AUTH_008", "Invalid refresh token", 401),
    AUTH_009("AUTH_009", "User not found", 401);

    private final String code;
    private final String message;
    private final int httpStatus;

    AuthErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
