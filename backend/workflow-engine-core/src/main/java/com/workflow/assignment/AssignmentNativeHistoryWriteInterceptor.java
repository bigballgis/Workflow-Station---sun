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

/** SDK process/task history mutations cannot remove protected reopen evidence; history reads retain their original behavior. */
@Component
public class AssignmentNativeHistoryWriteInterceptor implements HandlerInterceptor {
    private final AssignmentLegacyHistoryCheck history;
    public AssignmentNativeHistoryWriteInterceptor(AssignmentLegacyHistoryCheck history) { this.history=history; }
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        if (Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) || !(handler instanceof HandlerMethod method)
                || !method.getBeanType().getPackageName().equals("org.flowable.rest.service.api.history")) return true;
        Object attribute=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?,?> variables) {
            String process=identity(variables,"processInstanceId"),task=identity(variables,"taskId");
            if (process!=null) history.requireLegacyInstance(process);
            if (task!=null) history.requireLegacyTask(task);
        }
        // The SDK bulk process-history delete has its decoded selection checked by body advice; POST query APIs remain readable.
        AssignmentNativeRestScope.mark(request);
        return true;
    }
    private String identity(Map<?,?> variables,String name) {
        Object value=variables.get(name);
        if (value==null) return null;
        if (!(value instanceof String id) || id.isBlank()) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        return id;
    }
}
