package com.admin.exception;

/**
 * The operator may not put a function unit into a team, or deploy it there (platform access model §8.1, D-34) — 403.
 *
 * <p>Not an {@link AdminBusinessException}: callers that turn business errors into 400 must not swallow a refusal.</p>
 */
public class FunctionUnitTeamForbiddenException extends RuntimeException {

    public FunctionUnitTeamForbiddenException(String message) {
        super(message);
    }
}
