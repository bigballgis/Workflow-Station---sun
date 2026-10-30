package com.workflow.assignment;

import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

/** Security listeners surround the legacy best-effort assignment listener and always propagate failures. */
@Component
public class AssignmentCreationLifecycle {
    private final AssignmentCaseRegistry registry;
    private final AssignmentCreationCoordinator coordinator;

    public AssignmentCreationLifecycle(@org.springframework.context.annotation.Lazy AssignmentCaseRegistry registry,
            @org.springframework.context.annotation.Lazy AssignmentCreationCoordinator coordinator) {
        this.registry = registry; this.coordinator = coordinator;
    }

    public FlowableEventListener listener(boolean before) {
        return new FlowableEventListener() {
            @Override public void onEvent(FlowableEvent event) {
                if (!(event instanceof FlowableEntityEvent entity) || !(entity.getEntity() instanceof Task task)) {
                    throw new IllegalStateException("Assignment creation requires a Flowable task entity");
                }
                if (task.getProcessInstanceId()==null) {
                    if (task.getProcessDefinitionId()!=null) throw new IllegalStateException("Assignment task has no process context");
                    return; // Positively identified independent task, outside every workflow case.
                }
                if (before) {
                    if (registry.registerTask(task).isPresent()) AssignmentCreationBuffer.begin(task.getId());
                } else if (registry.instance(task.getProcessInstanceId()).isPresent()) {
                    coordinator.created(task);
                }
            }
            @Override public boolean isFailOnException() { return true; }
            @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
            @Override public String getOnTransaction() { return null; }
        };
    }
}
