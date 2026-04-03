package com.admin.exception;

import lombok.Getter;

/**
 * 资源冲突（HTTP 409），例如 UBR 已存在。
 */
@Getter
public class AdminConflictException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    public AdminConflictException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
