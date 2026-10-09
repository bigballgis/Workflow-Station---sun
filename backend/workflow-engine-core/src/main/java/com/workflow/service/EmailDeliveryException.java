package com.workflow.service;

import lombok.Getter;

/**
 * Failure while delivering a configured email (Send Task or post-action email).
 *
 * <p>{@code code} is the stable error code ({@code EMAIL_CONFIG_INVALID},
 * {@code EMAIL_CONNECTION_NOT_FOUND}, {@code EMAIL_SEND_FAILED}); {@code transientFailure}
 * tells retrying callers whether another attempt can succeed without a configuration change.</p>
 */
@Getter
public class EmailDeliveryException extends RuntimeException {

    public static final String CONFIG_INVALID = "EMAIL_CONFIG_INVALID";
    public static final String CONNECTION_NOT_FOUND = "EMAIL_CONNECTION_NOT_FOUND";
    public static final String SEND_FAILED = "EMAIL_SEND_FAILED";

    private final String code;
    private final boolean transientFailure;
    private final String rootCause;
    private final String causeChain;

    private EmailDeliveryException(String code, String message, boolean transientFailure,
                                   String rootCause, String causeChain, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.transientFailure = transientFailure;
        this.rootCause = rootCause;
        this.causeChain = causeChain;
    }

    public static EmailDeliveryException permanent(String code, String message) {
        return new EmailDeliveryException(code, message, false, null, null, null);
    }

    public static EmailDeliveryException sendFailed(String message, boolean transientFailure,
                                             String rootCause, String causeChain, Throwable cause) {
        return new EmailDeliveryException(SEND_FAILED, message, transientFailure, rootCause, causeChain, cause);
    }
}
