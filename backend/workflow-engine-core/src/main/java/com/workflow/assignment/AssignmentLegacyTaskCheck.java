package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Missing task projection is not proof of legacy: inspect immutable definitions and the real caller ancestry. */
@Component
public class AssignmentLegacyTaskCheck {
    private final TaskService tasks;
    private final RuntimeService runtime;
    private final AssignmentCaseRepository cases;
    private final AssignmentDefinitionPolicy definitions;
    public AssignmentLegacyTaskCheck(@Lazy TaskService tasks,@Lazy RuntimeService runtime,
            AssignmentCaseRepository cases,AssignmentDefinitionPolicy definitions) {
        this.tasks=tasks; this.runtime=runtime; this.cases=cases; this.definitions=definitions;
    }
    public void requireLegacy(String taskId) {
        var task=tasks.createTaskQuery().taskId(taskId).singleResult();
        if (task!=null) requireLegacy(task); // An absent task is left to the original command's not-found handling.
    }
    public void requireLegacy(Task task) {
        String instance=task.getProcessInstanceId();
        if (instance==null) {
            if (task.getProcessDefinitionId()!=null) throw unavailable();
            return; // Positively identified standalone Flowable task.
        }
        requireLegacyInstance(instance);
    }
    public void requireLegacyExecution(String executionId) {
        var execution=runtime.createExecutionQuery().executionId(executionId).singleResult();
        if (execution==null || execution.getProcessInstanceId()==null) throw unavailable();
        requireLegacyInstance(execution.getProcessInstanceId());
    }
    public void requireLegacyJob(org.flowable.job.api.Job job) {
        if (job==null) throw unavailable();
        if (job.getProcessInstanceId()!=null) requireLegacyInstance(job.getProcessInstanceId());
        else if (job.getExecutionId()!=null) requireLegacyExecution(job.getExecutionId());
        else if (job.getProcessDefinitionId()==null || definitions.tracked(job.getProcessDefinitionId())) throw unavailable();
    }
    public void requireLegacyInstance(String instance) {
        var visited=new java.util.HashSet<String>();
        while (visited.add(instance)) {
            if (cases.find(instance).isPresent()) throw unavailable();
            var process=runtime.createProcessInstanceQuery().processInstanceId(instance).singleResult();
            if (process==null || definitions.tracked(process.getProcessDefinitionId())) throw unavailable();
            var execution=runtime.createExecutionQuery().executionId(instance).singleResult();
            if (execution==null) throw unavailable();
            if (execution.getSuperExecutionId()==null) return;
            var caller=runtime.createExecutionQuery().executionId(execution.getSuperExecutionId()).singleResult();
            if (caller==null || caller.getProcessInstanceId()==null) throw unavailable();
            instance=caller.getProcessInstanceId();
        }
        throw unavailable();
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
