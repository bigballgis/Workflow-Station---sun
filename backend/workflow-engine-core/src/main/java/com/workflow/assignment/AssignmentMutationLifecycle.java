package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Rejects unguarded Flowable side entrances and records completion before synchronous successor creation. */
@Component
public class AssignmentMutationLifecycle implements FlowableEventListener {
    private final AssignmentTaskStateRepository states;
    private final AssignmentResponsibilityRepository records;
    private final AssignmentCaseRegistry registry;
    @org.springframework.beans.factory.annotation.Autowired @Lazy
    private AssignmentLegacyTaskCheck legacyCheck;

    public AssignmentMutationLifecycle(@Lazy AssignmentTaskStateRepository states,
            @Lazy AssignmentResponsibilityRepository records, @Lazy AssignmentCaseRegistry registry) {
        this.states = states; this.records = records; this.registry = registry;
    }

    @Override public void onEvent(FlowableEvent event) {
        if (event.getType()==FlowableEngineEventType.PROCESS_STARTED) {
            if (!(event instanceof FlowableEngineEvent process)) throw unavailable();
            registry.instance(process.getProcessInstanceId());
            return;
        }
        if (event.getType() == FlowableEngineEventType.PROCESS_COMPLETED || event.getType() == FlowableEngineEventType.PROCESS_CANCELLED) {
            if (!(event instanceof FlowableEngineEvent process)) throw new IllegalStateException("Assignment process context is unavailable");
            registry.ended(process.getProcessInstanceId(), event.getType() == FlowableEngineEventType.PROCESS_CANCELLED);
            return;
        }
        if (!(event instanceof FlowableEntityEvent entity) || !(entity.getEntity() instanceof Task task)) {
            throw new IllegalStateException("Assignment mutation requires a task entity");
        }
        if (states.find(task.getId()).isEmpty()) {
            // Flowable assigns its newly inserted entity before TASK_CREATED; that event must still qualify it before commit.
            if (event.getType()==FlowableEngineEventType.TASK_ASSIGNED
                    && task instanceof org.flowable.task.service.impl.persistence.entity.TaskEntity inserted && inserted.isInserted()
                    && !AssignmentCreationBuffer.finished(task.getId())) return;
            legacyCheck.requireLegacy(task); return;
        }
        if (event.getType() == FlowableEngineEventType.TASK_ASSIGNED && AssignmentCreationBuffer.active(task.getId())) return;
        if (event.getType() == FlowableEngineEventType.TASK_ASSIGNED && AssignmentProjectionScope.permitsRelease(task.getId(), task.getAssignee())) return;
        var authorized = AssignmentMutationScope.require(task.getId());
        if (event.getType() == FlowableEngineEventType.TASK_COMPLETED) {
            if (authorized.operation() != AssignmentMutationScope.Operation.COMPLETE) throw unavailable();
            records.completed(task.getId(), authorized.actor(), authorized.responsibleUsers());
        } else if (event.getType() == FlowableEngineEventType.TASK_ASSIGNED) {
            if (task.getAssignee() != null && !authorized.allowedHolders().contains(task.getAssignee())) throw unavailable();
        }
    }

    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
    @Override public boolean isFailOnException() { return true; }
    @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
    @Override public String getOnTransaction() { return null; }
}
