package com.admin.exception;

/**
 * The caller passed the endpoint's role check but the resource is outside what they may touch — a team they do not
 * lead (platform access model §8.2), or a platform role nobody may change (AC-13) — 403 {@code PERM_ACCESS_DENIED}
 * with a message that says why.
 *
 * <p>Not an {@link AdminBusinessException}: callers that turn business errors into 400 must not swallow a refusal.</p>
 */
public class AdminForbiddenException extends RuntimeException {

    public AdminForbiddenException(String message) {
        super(message);
    }
}
