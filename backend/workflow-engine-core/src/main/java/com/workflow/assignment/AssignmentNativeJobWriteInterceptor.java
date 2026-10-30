package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.flowable.engine.ManagementService;
import org.flowable.rest.service.api.management.JobResource;
import org.flowable.rest.service.api.management.JobCollectionResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.HashSet;

/** Flowable 7.2 workflow-continuation job actions; each SDK handler identifies its authoritative storage kind. */
@Component
public class AssignmentNativeJobWriteInterceptor implements HandlerInterceptor {
    private final ManagementService jobs;
    private final AssignmentLegacyTaskCheck legacy;
    public AssignmentNativeJobWriteInterceptor(@Lazy ManagementService jobs,AssignmentLegacyTaskCheck legacy) {
        this.jobs=jobs; this.legacy=legacy;
    }
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        if (Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) || !(handler instanceof HandlerMethod method)) return true;
        if (JobCollectionResource.class.isAssignableFrom(method.getBeanType())) {
            AssignmentNativeRestScope.mark(request); // The decoded selection is checked by AssignmentNativeBodyWriteAdvice.
            return true;
        }
        if (!JobResource.class.isAssignableFrom(method.getBeanType())) return true;
        String operation=method.getMethod().getName();
        // History-persistence jobs are not workflow continuations; their cleanup contract is audited separately.
        if (Set.of("executeHistoryJob","deleteHistoryJob").contains(operation)) return true;
        Object attribute=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?,?> variables) || !(variables.get("jobId") instanceof String id) || id.isBlank()) throw unavailable();
        var job=switch (operation) {
            case "executeJobAction","deleteJob" -> jobs.createJobQuery().jobId(id).singleResult();
            case "executeTimerJobAction","deleteTimerJob" -> jobs.createTimerJobQuery().jobId(id).singleResult();
            case "executeDeadLetterJobAction","deleteDeadLetterJob" -> jobs.createDeadLetterJobQuery().jobId(id).singleResult();
            case "deleteSuspendedJob" -> jobs.createSuspendedJobQuery().jobId(id).singleResult();
            default -> throw unavailable();
        };
        // Missing is not legacy: promotion may have moved the job between tables while this request was starting.
        legacy.requireLegacyJob(job);
        AssignmentNativeRestScope.mark(request);
        return true;
    }
    public void requireLegacyDeadLetterJobs(List<String> ids) {
        if (ids==null || ids.isEmpty() || ids.size()>1000 || ids.stream().anyMatch(id->id==null || id.isBlank())
                || new HashSet<>(ids).size()!=ids.size()) throw unavailable();
        var selected=jobs.createDeadLetterJobQuery().jobIds(ids).list();
        if (selected.size()!=ids.size()) throw unavailable();
        selected.forEach(legacy::requireLegacyJob); // Check the whole selection before the controller moves its first member.
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
