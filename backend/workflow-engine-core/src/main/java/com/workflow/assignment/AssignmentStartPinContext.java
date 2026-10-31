package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentStartContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Optional preflight assertion never grants authority: an actual definition change requires a fresh start request. */
public final class AssignmentStartPinContext {
    private AssignmentStartPinContext() { }
    public static void requireDefinition(String actual) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return;
        String expected=attributes.getRequest().getHeader(AssignmentStartContext.HEADER);
        if (expected!=null && !expected.equals(actual)) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
    }
}
