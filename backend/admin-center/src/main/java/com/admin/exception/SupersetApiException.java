package com.admin.exception;

/**
 * Superset API 调用异常
 */
public class SupersetApiException extends AdminBusinessException {

    public SupersetApiException(String message) {
        super("SUPERSET_API_ERROR", message);
    }

    public SupersetApiException(String message, Throwable cause) {
        super("SUPERSET_API_ERROR", message, cause);
    }
}
