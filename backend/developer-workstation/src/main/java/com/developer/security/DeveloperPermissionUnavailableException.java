package com.developer.security;

/**
 * Admin Center did not return a user's developer permissions and none are cached: the answer is unknown, not
 * "no permission" — reported as 503 so users and logs can tell an outage from a refusal.
 */
public class DeveloperPermissionUnavailableException extends RuntimeException {

    public DeveloperPermissionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
