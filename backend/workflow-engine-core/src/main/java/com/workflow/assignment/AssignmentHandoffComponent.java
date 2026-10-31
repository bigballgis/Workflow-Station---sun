package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

/** Only a server-recorded automatically released holder may use this narrow one-use operation. */
@Component
public class AssignmentHandoffComponent {
    private final AssignmentWriteExecutor writes;
    private final AssignmentTaskStateRepository states;
    private final TaskService tasks;
    private final ExtendedTaskInfoRepository extended;
    private final com.platform.messaging.support.NotificationDispatchHelper notifications;
    public AssignmentHandoffComponent(AssignmentWriteExecutor writes,AssignmentTaskStateRepository states,TaskService tasks,ExtendedTaskInfoRepository extended,
            com.platform.messaging.support.NotificationDispatchHelper notifications) {
        this.writes=writes; this.states=states; this.tasks=tasks; this.extended=extended; this.notifications=notifications;
    }
    public boolean handoff(String task,String actor,String target) {
        if (states.find(task).isEmpty()) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        return writes.execute(new AssignmentWriteGuard.Command(task,AssignmentMutationScope.Operation.HANDOFF,actor,null,target),()->{
            tasks.setAssignee(task,target);
            var info=extended.findByTaskIdAndIsDeletedFalse(task).orElseThrow(()->new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE));
            info.clearDelegation(); info.claimTask(target); info.setUpdatedBy(actor); extended.save(info);
            com.workflow.listener.TaskAssignmentNotifications.send(notifications,
                    new com.workflow.listener.TaskAssignmentNotifications.Notice(target,task,info.getTaskName(),info.getProcessInstanceId(),false));
            return true;
        });
    }
}
