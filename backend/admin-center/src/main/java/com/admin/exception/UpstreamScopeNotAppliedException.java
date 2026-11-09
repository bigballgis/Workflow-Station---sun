package com.admin.exception;

/**
 * A backend Admin relies on to filter by team (the User Portal audit) answered with rows outside the caller's teams:
 * the result is withheld, never passed on — 502, the upstream broke its contract (platform access model §8.2).
 */
public class UpstreamScopeNotAppliedException extends RuntimeException {

    public UpstreamScopeNotAppliedException(String message) {
        super(message);
    }
}
