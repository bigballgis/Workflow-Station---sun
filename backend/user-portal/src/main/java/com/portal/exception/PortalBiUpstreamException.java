package com.portal.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PortalBiUpstreamException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public PortalBiUpstreamException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
