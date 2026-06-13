package com.portal.util;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.Callable;

/**
 * Propagates the caller's request attributes and security context into async worker threads
 * so outbound calls (e.g. to workflow-engine) keep the original Authorization header.
 */
public final class RequestContextInheritanceUtils {

    private RequestContextInheritanceUtils() {
    }

    /**
     * Inherit the current request's Authorization and security context in an async thread
     * for forwarding to workflow-engine.
     */
    public static <T> T runWithInheritedRequestAndSecurity(
            SecurityContext securityContext,
            ServletRequestAttributes requestAttributes,
            Callable<T> action) {
        try {
            if (requestAttributes != null) {
                RequestContextHolder.setRequestAttributes(requestAttributes, true);
            }
            SecurityContextHolder.setContext(securityContext);
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            SecurityContextHolder.clearContext();
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
