package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.util.JsonUtils;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.jobexecutor.*;
import org.flowable.job.service.JobHandler;
import org.flowable.job.service.impl.persistence.entity.JobEntity;
import org.flowable.variable.api.delegate.VariableScope;
import java.util.List;

/** Recheck at execution: an originally legacy definition can acquire protected references while its timer is waiting. */
public final class AssignmentDefinitionActionJobs {
    private AssignmentDefinitionActionJobs() { }
    public static List<JobHandler> handlers(AssignmentDefinitionRetention retention) {
        return List.of(new TimerSuspendProcessDefinitionHandler() {
            @Override public void execute(JobEntity job,String configuration,VariableScope scope,CommandContext context) {
                check(retention,job,configuration); super.execute(job,configuration,scope,context);
            }
        },new TimerActivateProcessDefinitionHandler() {
            @Override public void execute(JobEntity job,String configuration,VariableScope scope,CommandContext context) {
                check(retention,job,configuration); super.execute(job,configuration,scope,context);
            }
        });
    }
    private static void check(AssignmentDefinitionRetention retention,JobEntity job,String configuration) {
        try {
            var node=JsonUtils.getObjectMapper().readTree(configuration);
            if (node==null) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
            if (TimerChangeProcessDefinitionSuspensionStateJobHandler.getIncludeProcessInstances(node)) {
                if (job.getProcessDefinitionId()==null || job.getProcessDefinitionId().isBlank()) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
                retention.cascade(job.getProcessDefinitionId());
            }
        } catch (java.io.IOException invalid) { throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE,invalid); }
    }
}
