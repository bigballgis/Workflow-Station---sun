package com.platform.security.exception;

/**
 * Authentication error codes.
 * Validates: Requirements 2.2, 2.3, 2.4, 4.2, 4.3
 */
public enum AuthErrorCode {
    
    AUTH_001("AUTH_001", "用户名或密码错误", 401),
    AUTH_002("AUTH_002", "账户已锁定", 403),
    AUTH_003("AUTH_003", "账户未激活", 403),
    AUTH_004("AUTH_004", "令牌已过期", 401),
    AUTH_005("AUTH_005", "令牌无效", 401),
    AUTH_006("AUTH_006", "令牌已被注销", 401),
    AUTH_007("AUTH_007", "刷新令牌已过期", 401),
    AUTH_008("AUTH_008", "刷新令牌无效", 401),
    AUTH_009("AUTH_009", "用户不存在", 401);

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
