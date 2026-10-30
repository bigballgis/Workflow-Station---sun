package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.flowable.rest.service.api.BulkDeleteInstancesRestActionRequest;
import org.flowable.rest.service.api.runtime.task.BulkTasksRequest;
import org.flowable.rest.service.api.runtime.task.TaskRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import java.lang.reflect.Type;

/** URI interception cannot see collection targets. Validate the SDK's decoded command before any batch member is mutated. */
@ControllerAdvice(basePackages={"org.flowable.rest.service.api.runtime","org.flowable.rest.service.api.management","org.flowable.rest.service.api.history"})
public class AssignmentNativeBodyWriteAdvice extends RequestBodyAdviceAdapter {
    private final AssignmentLegacyTaskCheck legacy;
    private final AssignmentNativeJobWriteInterceptor jobs;
    private final AssignmentLegacyHistoryCheck history;
    public AssignmentNativeBodyWriteAdvice(AssignmentLegacyTaskCheck legacy,AssignmentNativeJobWriteInterceptor jobs,AssignmentLegacyHistoryCheck history) {
        this.legacy=legacy; this.jobs=jobs; this.history=history;
    }
    @Override public boolean supports(MethodParameter parameter,Type targetType,Class<? extends HttpMessageConverter<?>> converter) {
        return TaskRequest.class.isAssignableFrom(parameter.getParameterType())
                || BulkDeleteInstancesRestActionRequest.class.isAssignableFrom(parameter.getParameterType())
                || org.flowable.rest.service.api.BulkMoveDeadLetterActionRequest.class.isAssignableFrom(parameter.getParameterType());
    }
    @Override public Object afterBodyRead(Object body,HttpInputMessage input,MethodParameter parameter,Type type,
            Class<? extends HttpMessageConverter<?>> converter) {
        if (body instanceof org.flowable.rest.service.api.BulkMoveDeadLetterActionRequest selected) {
            jobs.requireLegacyDeadLetterJobs(selected.getJobIds());
        }
        if (body instanceof BulkTasksRequest tasks && tasks.getTaskIds()!=null) {
            tasks.getTaskIds().forEach(id->legacy.requireLegacy(identity(id)));
        }
        if (body instanceof BulkDeleteInstancesRestActionRequest processes && processes.getInstanceIds()!=null) {
            boolean historical=parameter.getDeclaringClass().getPackageName().equals("org.flowable.rest.service.api.history");
            if (processes.getInstanceIds().size()>1000) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
            for (String id:processes.getInstanceIds()) {
                if (historical) history.requireLegacyInstance(identity(id));
                else legacy.requireLegacyInstance(identity(id));
            }
        }
        if (body instanceof TaskRequest task && task.getParentTaskId()!=null) legacy.requireLegacy(identity(task.getParentTaskId()));
        return body;
    }
    private String identity(String value) {
        if (value==null || value.isBlank()) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        return value;
    }
}
