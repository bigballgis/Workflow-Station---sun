package com.admin.exception;

import lombok.Getter;

/**
 * A service the request depends on cannot be reached right now (HTTP 503), for example the Redis lock store of the
 * LDAP sync. Retrying later may succeed.
 */
@Getter
public class AdminServiceUnavailableException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    public AdminServiceUnavailableException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
