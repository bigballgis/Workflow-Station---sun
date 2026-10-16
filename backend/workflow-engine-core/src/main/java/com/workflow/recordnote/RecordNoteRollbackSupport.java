package com.workflow.recordnote;

import com.platform.common.recordnote.RecordNoteRequestLock;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.*;
import org.flowable.engine.RepositoryService;
import org.flowable.task.api.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Invalidates the affected row's downstream snapshots before changing activity state. */
@Component
@RequiredArgsConstructor
public class RecordNoteRollbackSupport {
    private final RecordNoteRoundRepository rounds;
    private final RepositoryService definitions;
    private final JdbcTemplate jdbc;

    public void returning(Task task,String targetNode) {
        var current=rounds.current(task.getProcessInstanceId());
        if (current.isEmpty()) return;
        RecordNoteRequestLock.acquire(jdbc,task.getProcessInstanceId());
        var row=current.stream().filter(t -> t.taskId().equals(task.getId())).findFirst();
        var target=definitions.getBpmnModel(task.getProcessDefinitionId()).getFlowElement(targetNode);
        var scope=RecordNoteTaskScope.activity(target);
        boolean sameRow=target instanceof UserTask && scope!=null && row.isPresent()
                && scope.getId().equals(row.get().scopeKey());
        List<String> nodes=new ArrayList<>();
        visit(target,new HashSet<>(),nodes);
        String activation=scope==null || row.isEmpty()?null:row.get().activationContext().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(scope.getId()+":"))
                .map(java.util.Map.Entry::getValue).findFirst().orElse(null);
        if(!sameRow && activation!=null) rounds.invalidate(task.getProcessInstanceId(),nodes,null,activation);
        else rounds.invalidate(task.getProcessInstanceId(),nodes,sameRow?row.get().rowKey():null);
    }

    private void visit(FlowElement element,Set<String> seen,List<String> nodes) {
        if (element==null || !seen.add(element.getId())) return;
        if (element instanceof UserTask) nodes.add(element.getId());
        if (element instanceof SubProcess sub) for (FlowElement child : sub.getFlowElements()) visit(child,seen,nodes);
        if (element instanceof FlowNode flow) for (SequenceFlow edge : flow.getOutgoingFlows()) {
            visit(edge.getTargetFlowElement(),seen,nodes);
        }
    }
}
