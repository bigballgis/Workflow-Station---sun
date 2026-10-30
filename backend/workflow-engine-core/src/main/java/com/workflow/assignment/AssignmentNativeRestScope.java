package com.workflow.assignment;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Server-owned request attribute; Spring owns request/thread cleanup, including failed and async dispatches. */
final class AssignmentNativeRestScope {
    private static final String WRITE=AssignmentNativeRestScope.class.getName()+".write";
    private AssignmentNativeRestScope() { }
    static void mark(HttpServletRequest request) { request.setAttribute(WRITE,Boolean.TRUE); }
    static boolean active() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && Boolean.TRUE.equals(attributes.getRequest().getAttribute(WRITE));
    }
}
