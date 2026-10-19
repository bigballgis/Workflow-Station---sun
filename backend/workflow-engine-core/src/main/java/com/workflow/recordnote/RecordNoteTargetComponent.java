package com.workflow.recordnote;

import com.platform.common.recordnote.RecordNoteRuntimeContext;
import com.platform.common.recordnote.RecordNoteRuntimeContext.TargetDefinition;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Batch, read-only resolution. The caller owns the request delivery lock, never reacquire it here. */
@Component
@RequiredArgsConstructor
public class RecordNoteTargetComponent {
    private final HistoryService history;
    private final RepositoryService repository;
    private final RecordNoteTaskContext context;
    private final RecordNoteRoundRepository rounds;

    @Transactional(readOnly=true)
    public RecordNoteRuntimeContext resolve(String processId,List<String> nodes) {
        if (nodes.isEmpty() || nodes.size()>50) throw new IllegalArgumentException("Invalid target nodes");
        var process=history.createHistoricProcessInstanceQuery().processInstanceId(processId).singleResult();
        if (process==null) throw new IllegalArgumentException("Request does not exist");
        String definition=process.getProcessDefinitionId();
        var model=repository.getBpmnModel(definition);
        List<TargetDefinition> definitions=new ArrayList<>();
        for (String node : nodes) {
            if (!(model.getFlowElement(node) instanceof UserTask task)) {
                throw new IllegalArgumentException("Notification target must be a user task");
            }
            String table=property(definition,processId,node,"subTableName");
            String person=property(definition,processId,node,"assigneeField");
            String role=property(definition,processId,node,"roleField");
            if (table==null || (person==null && role==null)) {
                throw new IllegalArgumentException("Notification target has no configured row assignment");
            }
            String scope=RecordNoteTaskScope.key(task);
            definitions.add(new TargetDefinition(node,scope,table,person,role));
        }
        return new RecordNoteRuntimeContext(definitions,rounds.current(processId).stream()
                .filter(t -> nodes.contains(t.nodeKey())).toList(),process.getDeleteReason()!=null);
    }

    private String property(String definition,String process,String node,String name) {
        return context.property(definition,process,node,name);
    }
}
