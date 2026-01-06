package com.platform.security.exception;

/**
 * Authentication exception for login/logout/token errors.
 * Validates: Requirements 2.2, 2.3, 2.4, 4.2, 4.3
 */
public class AuthenticationException extends RuntimeException {

    private final AuthErrorCode errorCode;

    public AuthenticationException(AuthErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AuthenticationException(AuthErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AuthenticationException(AuthErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public AuthErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }

    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }
}
