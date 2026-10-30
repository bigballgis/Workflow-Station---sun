package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import com.portal.client.AssignmentEngineTransport;
import com.portal.dto.TaskInfo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

@Component
public class AssignmentSubTableAssignment {
    private static final ParameterizedTypeReference<ApiResponse<AssignmentSubTablePlan>> TYPE=new ParameterizedTypeReference<>() { };
    private final AssignmentEngineTransport engine;
    private final AssignmentPortalWriteExecutor writes;
    private final AssignmentSubTableWriter writer;
    private final MineTaskListCache mine;
    private final MiOverlayComponent overlays;
    public AssignmentSubTableAssignment(AssignmentEngineTransport engine,AssignmentPortalWriteExecutor writes,
            AssignmentSubTableWriter writer,MineTaskListCache mine,MiOverlayComponent overlays) {
        this.engine=engine; this.writes=writes; this.writer=writer; this.mine=mine; this.overlays=overlays;
    }
    public Optional<Map<String,Object>> assign(TaskInfo task,Long rowId,Map<String,Object> rowKey,String target,String actor) {
        var plan=engine.post(List.of("sub-table-plans"),new AssignmentSubTablePlan.Request(task.getTaskId(),target),TYPE,false);
        if (plan==null || !Objects.equals(task.getTaskId(),plan.taskId()) || !Objects.equals(task.getProcessInstanceId(),plan.instanceId())
                || !Objects.equals(task.getProcessDefinitionId(),plan.definitionId())) throw unavailable();
        if (!plan.tracked()) return Optional.empty();
        if (!target.equals(plan.target())) throw unavailable();
        var request=AssignmentPortalWriteGuard.Request.of(task.getTaskId(),actor,"SUBTABLE_ASSIGN",
                new Payload(plan,rowId,rowKey),task.getProcessInstanceId());
        return Optional.of(writes.execute(request,true,()->{
            var result=writer.assign(plan,rowId,rowKey);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { mine.invalidate(); overlays.invalidate(plan.instanceId()); }
            });
            return result;
        }));
    }
    private record Payload(AssignmentSubTablePlan plan,Long rowId,Map<String,Object> rowKey) { }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
