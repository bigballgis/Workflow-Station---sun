package com.portal.exception;

import org.springframework.http.HttpStatus;

/**
 * The HTTP status a {@link PortalException} answers with, for the controllers that handle it: its code when the code
 * is an HTTP error status (4xx or 5xx, e.g. an engine refusal passed on as it came), otherwise 500.
 */
public final class PortalExceptionStatus {

    private PortalExceptionStatus() {
    }

    public static int of(PortalException e) {
        String code = e.getCode();
        if (code != null && code.matches("[45]\\d\\d")) {
            HttpStatus status = HttpStatus.resolve(Integer.parseInt(code));
            if (status != null) {
                return status.value();
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
}
