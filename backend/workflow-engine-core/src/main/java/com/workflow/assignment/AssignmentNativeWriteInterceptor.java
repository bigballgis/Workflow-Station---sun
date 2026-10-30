package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Map;
import java.util.Set;

/** Native REST has no Portal effect/final-check scope. Protected resource writes use the platform commands. */
@Component
public class AssignmentNativeWriteInterceptor implements HandlerInterceptor {
    private static final String RUNTIME="org.flowable.rest.service.api.runtime";
    private final AssignmentLegacyTaskCheck legacy;
    public AssignmentNativeWriteInterceptor(AssignmentLegacyTaskCheck legacy) { this.legacy=legacy; }
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        if (Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) || !(handler instanceof HandlerMethod method)
                || !(method.getBeanType().getPackageName().equals(RUNTIME)
                || method.getBeanType().getPackageName().startsWith(RUNTIME+"."))) return true;
        Object attribute=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?,?> variables) {
            String task=identity(variables,"taskId"), execution=identity(variables,"executionId"), process=identity(variables,"processInstanceId");
            if (task!=null) legacy.requireLegacy(task);
            if (execution!=null) legacy.requireLegacyExecution(execution);
            if (process!=null) legacy.requireLegacyInstance(process);
        }
        AssignmentNativeRestScope.mark(request);
        return true;
    }
    private String identity(Map<?,?> variables,String name) {
        Object value=variables.get(name);
        if (value==null) return null;
        if (!(value instanceof String id) || id.isBlank()) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
        return id;
    }
}
