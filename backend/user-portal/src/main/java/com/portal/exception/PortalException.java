package com.portal.exception;

/**
 * 门户业务异常
 */
public class PortalException extends RuntimeException {

    private final String code;

    public PortalException(String message) {
        super(message);
        this.code = "500";
    }

    public PortalException(String code, String message) {
        super(message);
        this.code = code;
    }

    public PortalException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
