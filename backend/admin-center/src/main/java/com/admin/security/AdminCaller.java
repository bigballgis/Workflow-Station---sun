package com.admin.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collection;
import java.util.Set;

/**
 * Who is calling an Admin endpoint, as {@link AdminAccessInterceptor} admitted them; stored on the request so list
 * filters, row flags and request-body checks judge the same caller (platform access model WP9, decision 1).
 *
 * @param service true for a first-party service call admitted by its token alone; no team filter applies
 * @param userId  the signed-in user, or the user a service call acts for; null for a service call naming none
 * @param roles   platform role codes the interceptor judged
 */
public record AdminCaller(boolean service, String userId, Set<String> roles) {

    static final String REQUEST_ATTRIBUTE = AdminCaller.class.getName();

    public static AdminCaller ofService() {
        return new AdminCaller(true, null, Set.of());
    }

    public static AdminCaller ofUser(String userId, Collection<String> roles) {
        return new AdminCaller(false, userId, Set.copyOf(roles));
    }

    public boolean isSysAdmin() {
        return !service && roles.contains("SYS_ADMIN");
    }

    /** Service calls, System Administrators and Auditors read every team's resources (D-43). */
    public boolean readsAllTeams() {
        return service || isSysAdmin() || roles.contains("AUDITOR");
    }

    /** Service calls and System Administrators change every team's resources. */
    public boolean writesAllTeams() {
        return service || isSysAdmin();
    }

    /** The caller the interceptor admitted for this request; absent before it ran or on a public endpoint. */
    public static AdminCaller of(HttpServletRequest request) {
        Object caller = request.getAttribute(REQUEST_ATTRIBUTE);
        if (caller instanceof AdminCaller admitted) {
            return admitted;
        }
        throw new IllegalStateException("No admitted Admin caller on " + request.getRequestURI());
    }

    void storeOn(HttpServletRequest request) {
        request.setAttribute(REQUEST_ATTRIBUTE, this);
    }
}
