package com.workflow.recordnote;

import com.platform.common.recordnote.RecordNoteRequestLock;
import com.platform.common.recordnote.RecordNoteTaskTarget;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.delegate.event.*;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Runs inside Flowable's transaction, serializing state commits with final note delivery. */
@Component
@RequiredArgsConstructor
public class RecordNoteLifecycleListener implements FlowableEventListener {
    @Lazy private final RecordNoteTaskContext context;
    private final RecordNoteRoundRepository rounds;
    private final JdbcTemplate jdbc;

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEngineEvent engine) || engine.getProcessInstanceId()==null || engine.getProcessDefinitionId()==null
                || !context.enabled(engine.getProcessDefinitionId(),engine.getProcessInstanceId())) return;
        RecordNoteRequestLock.acquire(jdbc,engine.getProcessInstanceId());
        if (event instanceof FlowableEntityEvent entity && entity.getEntity() instanceof Task task) {
            taskEvent(event.getType(),task);
        } else if (event instanceof FlowableActivityEvent activity) {
            if (event.getType()==FlowableEngineEventType.ACTIVITY_CANCELLED) {
                rounds.cancelExecution(engine.getProcessInstanceId(),activity.getActivityId(),activity.getExecutionId());
            } else if (event.getType()==FlowableEngineEventType.MULTI_INSTANCE_ACTIVITY_CANCELLED) {
                rounds.cancelActivation(engine.getProcessInstanceId(),activity.getExecutionId());
            } else if (event.getType()==FlowableEngineEventType.MULTI_INSTANCE_ACTIVITY_STARTED) {
                rounds.activate(engine.getProcessInstanceId(),context.activationForScope(activity.getExecutionId(),activity.getActivityId()));
            }
        }
    }

    private void taskEvent(FlowableEventType type,Task task) {
        if (type==FlowableEngineEventType.TASK_CREATED) {
            RecordNoteTaskTarget target=context.capture(task);
            if (target!=null) rounds.created(task.getProcessInstanceId(),task.getProcessDefinitionId(),task.getExecutionId(),target);
            return;
        }
        var target=rounds.task(task.getId());
        if(target.isEmpty()) {
            if(type==FlowableEngineEventType.TASK_ASSIGNED) {
                var recovered=context.capture(task);
                if(recovered!=null) rounds.created(task.getProcessInstanceId(),task.getProcessDefinitionId(),task.getExecutionId(),recovered);
            }
            return;
        }
        if (type==FlowableEngineEventType.TASK_ASSIGNED) {
            if ("TEAM".equals(target.get().assignmentMode())) {
                String assignee=task.getAssignee();
                rounds.changed(task.getId(),assignee,assignee==null?"WAITING_CLAIM":"ACTIVE");
            }
        } else if (type==FlowableEngineEventType.TASK_COMPLETED) {
            String recipient="PERSON".equals(target.get().assignmentMode())
                    ? context.personAtCompletion(task,target.get()) : task.getAssignee();
            rounds.changed(task.getId(),recipient,recipient==null?"UNRESOLVED":"COMPLETED");
        }
    }

    @Override public boolean isFailOnException() { return true; }
    @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
    @Override public String getOnTransaction() { return null; }
}
