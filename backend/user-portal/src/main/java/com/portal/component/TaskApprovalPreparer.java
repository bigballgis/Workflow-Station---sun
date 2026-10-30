package com.portal.component;

import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.platform.common.functionunit.RuntimeVersionContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Existing approval normalization, separated from remote completion and local persistence. */
final class TaskApprovalPreparer {
    private final TaskApprovalCompletionComponent owner;
    private final boolean protectedAssignment;
    TaskApprovalPreparer(TaskApprovalCompletionComponent owner,boolean protectedAssignment) {
        this.owner=owner; this.protectedAssignment=protectedAssignment;
    }

    TaskApprovalPreparation prepare(TaskInfo task,TaskCompleteRequest request,String actor,String username) {
        RuntimeVersionContext.rejectClientMetadata(request.getVariables());
        RuntimeVersionContext.rejectClientMetadata(request.getFormData());
        owner.taskForms().validateSubTableBatchChanges(task.getTaskDefinitionKey(),task.getProcessInstanceId(),request.getSubTableBatchChanges());
        if (!owner.engine().isAvailable()) throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        Submission submitted=submission(task,request,actor);
        Map<String,Object> variables=submitted.variables();
        outcome(request,variables);
        var mi=multiInstance(task,request,submitted);
        Map<String,Object> engine=owner.mergeApprovalVariables(task.getProcessInstanceId(),variables);
        if (protectedAssignment) engine=com.platform.common.assignment.AssignmentSnapshotMetadata.engineInput(engine);
        SubTableWriteIsolation.stripTransportMetadata(engine);
        if (mi.context()!=null) OwnerFieldComponent.stripProcessWideCurrentItem(engine);
        String represented=null;
        if (owner.permissions().isSingleTaskDelegatee(task,actor,username) && task.getAssignee()!=null && !task.getAssignee().isBlank()) represented=task.getAssignee();
        return new TaskApprovalPreparation(task,request,actor,variables,engine,submitted.snapshot(),submitted.hasSubTables(),
                mi.context(),mi.expected(),mi.definition(),represented);
    }

    private Submission submission(TaskInfo task,TaskCompleteRequest request,String actor) {
        var forms=owner.taskForms();
        Map<String,Object> variables=new HashMap<>();
        if (request.getVariables()!=null) variables.putAll(request.getVariables());
        if (request.getFormData()!=null) variables.putAll(request.getFormData());
        variables=new HashMap<>(forms.prepareSubTableSubmission(task.getProcessInstanceId(),task.getTaskDefinitionKey(),variables,
                actor,request.getEmptiedSubTableKeys(),request.getSubTableBindingScopes(),request.getSubTableBatchChanges()));
        var permitted=forms.filterVersionedSubmission(task.getProcessInstanceId(),task.getTaskDefinitionKey(),variables);
        variables.clear(); variables.putAll(permitted); SubTableWriteIsolation.stripTransportMetadata(variables);
        Map<String,Object> snapshot=forms.copyChangeHistorySubmission(request.getFormData());
        if (snapshot==null) snapshot=request.getFormData()==null?new HashMap<>():new HashMap<>(request.getFormData());
        SubTableWriteIsolation.stripTransportMetadata(snapshot);
        Map<String,Object> filtered=forms.filterTaskSubmissionForChangeHistory(task.getProcessInstanceId(),task.getTaskDefinitionKey(),snapshot,variables);
        boolean hasTables=filtered!=null && filtered.containsKey("__subTables__");
        Map<String,Object> explicit=new LinkedHashMap<>();
        if (hasTables && snapshot.get("__subTables__") instanceof Map<?,?> tables) {
            tables.forEach((key,value)->{if (key!=null) explicit.put(String.valueOf(key),value);});
        }
        return new Submission(variables,snapshot,hasTables,explicit);
    }

    private MiPreparation multiInstance(TaskInfo task,TaskCompleteRequest request,Submission submitted) {
        var variables=submitted.variables();
        owner.miCollections().mergeSubTablesFromTaskInfoForMi(task,variables);
        var context=owner.isolateOutboundSubTables(task,request,variables,protectedAssignment);
        owner.taskForms().validateSubTableBatchPayload(task.getTaskDefinitionKey(),task.getProcessInstanceId(),request.getSubTableBatchChanges(),request.getSubTableBindingScopes(),variables);
        List<SubTableBatchInitialMiOutcome.Expected> expected=List.of();
        String definition=null;
        if (context!=null) {
            ProcessInstance instance=owner.instances().findById(task.getProcessInstanceId()).orElseThrow(()->new IllegalStateException("Process instance is missing at completion"));
            definition=instance.getProcessDefinitionId();
            if (instance.getVariables()!=null && instance.getVariables().containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD)) {
                expected=owner.miCollections().injectMiCollectionFromBpmn(task.getProcessDefinitionKey(),task.getTaskDefinitionKey(),task.getProcessInstanceId(),variables,instance.getVariables());
            } else owner.miCollections().injectMiCollectionFromBpmn(task.getProcessDefinitionKey(),task.getTaskDefinitionKey(),task.getProcessInstanceId(),variables,true);
        } else owner.miCollections().injectMiCollectionFromBpmn(task.getProcessDefinitionKey(),task.getTaskDefinitionKey(),task.getProcessInstanceId(),variables);
        owner.preserveEngineSubTablesOnComplete(task.getProcessInstanceId(),variables,submitted.explicitTables(),
                SubTableBatchDeletedRows.read(owner.instances().findById(task.getProcessInstanceId())
                        .orElseThrow(()->new IllegalStateException("Process instance is missing at completion")).getVariables()));
        return new MiPreparation(context,expected,definition);
    }

    private void outcome(TaskCompleteRequest request,Map<String,Object> variables) {
        variables.put("action",request.getAction());
        if ("APPROVE".equals(request.getAction())) { variables.put("decision","yes"); variables.put("approvalStatus","APPROVED"); }
        else if ("REJECT".equals(request.getAction())) { variables.put("decision","no"); variables.put("approvalStatus","REJECTED"); }
        if (request.getComment()!=null && !request.getComment().isEmpty()) variables.put("approverComments",request.getComment());
    }
    private record Submission(Map<String,Object> variables,Map<String,Object> snapshot,boolean hasSubTables,Map<String,Object> explicitTables) { }
    private record MiPreparation(Map<String,Object> context,List<SubTableBatchInitialMiOutcome.Expected> expected,String definition) { }
}
