package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.platform.common.assignment.AssignmentCompletionRequest;
import com.platform.common.assignment.AssignmentVariablePatch;
import com.platform.common.util.JsonUtils;
import com.portal.dto.ChangeHistoryContext;
import com.portal.entity.ChangeHistory;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Freezes the original Portal effect before Engine acceptance; recovery never recalculates it. */
@Component
public class AssignmentCompletionEffectBuilder {
    private final TaskApprovalCompletionComponent approval;
    private final ChangeHistoryComponent history;
    private final com.portal.repository.AssignmentCompletionBaselineRepository baselines;
    private final ActionPostEmailComponentImpl emails;
    public AssignmentCompletionEffectBuilder(TaskApprovalCompletionComponent approval,ChangeHistoryComponent history,
            com.portal.repository.AssignmentCompletionBaselineRepository baselines,ActionPostEmailComponentImpl emails) {
        this.approval=approval; this.history=history; this.baselines=baselines; this.emails=emails;
    }
    TaskApprovalPreparation prepare(com.portal.dto.TaskInfo task,com.portal.dto.TaskCompleteRequest request,String actor,String username) {
        if (!approval.permissions().canProcessTask(task,actor,username)) {
            throw new com.portal.exception.PortalException("403","You do not have permission to process this task");
        }
        var prepared=approval.prepare(task,request,actor,username,true);
        String nominal=task.getAssignee()!=null && !task.getAssignee().equals(actor)?task.getAssignee():null;
        return new TaskApprovalPreparation(task,request,actor,prepared.variables(),prepared.engineVariables(),prepared.submitted(),
                prepared.submittedSubTables(),prepared.writeContext(),prepared.expected(),prepared.definitionId(),nominal);
    }
    AssignmentCompletionRequest.Effect build(TaskApprovalPreparation prepared,String clientDigest) {
        var full=approval.portalValues(prepared);
        String task=prepared.task().getTaskId();
        var values=new TaskApprovalPortalValues(com.platform.common.assignment.AssignmentSnapshotMetadata.baseline(full.baseline(),task),
                com.platform.common.assignment.AssignmentSnapshotMetadata.baseline(full.accepted(),task),full.catalogId());
        var original=baselines.capture(prepared.task().getProcessInstanceId(),task);
        if (original.stream().noneMatch(value->value.instanceId().equals(prepared.task().getProcessInstanceId()) && value.values().equals(values.baseline()))) {
            throw new com.platform.common.assignment.AssignmentFailure(com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
        }
        var records=history(prepared,values);
        Map<String,Object> observation=Map.of();
        if (!prepared.expected().isEmpty()) observation=JsonUtils.getObjectMapper().convertValue(
                new SubTableBatchInitialMiOutcome.Plan(prepared.task().getProcessInstanceId(),prepared.definitionId(),prepared.expected()),new TypeReference<>() { });
        return new AssignmentCompletionRequest.Effect(prepared.task().getProcessInstanceId(),AssignmentVariablePatch.between(values.baseline(),values.accepted()),
                prepared.task().getTaskDefinitionKey(),clientDigest,records.stream().map(this::audit).toList(),observation,original,mail(prepared.request()));
    }
    private Map<String,Object> mail(com.portal.dto.TaskCompleteRequest request) {
        var plan=emails.prepareCompletion(request);
        if (plan.isEmpty()) return Map.of(); // Explicitly no enabled mail rule on the original bound Action.
        return JsonUtils.getObjectMapper().convertValue(plan.get(),new TypeReference<>() { });
    }
    private List<ChangeHistory> history(TaskApprovalPreparation prepared,TaskApprovalPortalValues values) {
        var task=prepared.task(); var forms=approval.taskForms(); String process=task.getProcessInstanceId(), stage=task.getTaskDefinitionKey();
        Map<String,Object> baseline=JsonUtils.getObjectMapper().convertValue(values.baseline(),new TypeReference<>() { });
        var submitted=forms.filterTaskSubmissionForChangeHistory(process,stage,prepared.submitted(),prepared.variables());
        if (submitted==null) submitted=new java.util.HashMap<>(prepared.submitted());
        if (submitted.isEmpty()) return List.of();
        var context=ChangeHistoryContext.builder().processInstanceId(process).taskInstanceId(task.getTaskId()).stageId(stage).userId(prepared.actor()).build();
        var result=new ArrayList<>(history.prepareFieldChanges(context,forms.filterTaskFieldBaselineForChangeHistory(process,stage,baseline),submitted));
        if (prepared.submittedSubTables() && submitted.get("__subTables__")!=null) {
            Object oldRows=forms.filterTaskSubTableBaselineForChangeHistory(process,stage,baseline.get("__subTables__"));
            rows(context,oldRows,submitted.get("__subTables__"),values.catalogId(),result);
        }
        return List.copyOf(result);
    }
    private void rows(ChangeHistoryContext context,Object oldSource,Object newSource,String catalog,List<ChangeHistory> result) {
        var keys=history.pinnedPrimaryKeyResolver(catalog);
        var oldRows=ChangeHistoryComponent.normalizeSubTableRowsByHistoryName(oldSource,keys);
        var newRows=ChangeHistoryComponent.normalizeSubTableRowsByHistoryName(newSource,keys);
        for (var entry:newRows.entrySet()) {
            var changes=SubTableChangeHistoryDiff.compute(oldRows.getOrDefault(entry.getKey(),List.of()),entry.getValue(),
                    history.pinnedPrimaryKeyFieldsForSliceKey(catalog,entry.getKey()));
            result.addAll(history.prepareSubTableChanges(context,entry.getKey(),changes));
        }
    }
    private AssignmentCompletionRequest.AuditChange audit(ChangeHistory record) {
        return new AssignmentCompletionRequest.AuditChange(record.getFieldName(),record.getOldValue(),record.getNewValue(),
                AssignmentCompletionRequest.AuditKind.valueOf(record.getChangeType().name()),record.getSubTableName(),record.getRowIdentifier(),
                record.getTimestamp(),record.getBatchId(),record.getBatchTime());
    }
}
