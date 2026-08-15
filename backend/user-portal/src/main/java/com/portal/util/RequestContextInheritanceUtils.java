package com.portal.util;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
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
     *
     * <p>Restores whatever context the running thread had instead of clearing it: the portal fan-out
     * pools use {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}, so once a pool is
     * saturated the task runs <em>inline on the request thread</em>. Clearing unconditionally would
     * strip that request's own Authorization for everything it still has to do, turning pool
     * saturation into sporadic 401/403s from workflow-engine. On a real worker thread the previous
     * values are the empty context / {@code null}, so this stays equivalent to clearing.</p>
     */
    public static <T> T runWithInheritedRequestAndSecurity(
            SecurityContext securityContext,
            ServletRequestAttributes requestAttributes,
            Callable<T> action) {
        SecurityContext previousContext = SecurityContextHolder.getContext();
        RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
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
            SecurityContextHolder.setContext(previousContext);
            RequestContextHolder.setRequestAttributes(previousAttributes);
        }
    }
}
