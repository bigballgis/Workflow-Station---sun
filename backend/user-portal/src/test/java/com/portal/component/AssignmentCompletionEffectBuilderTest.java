package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskCompleteRequest;
import com.portal.entity.ChangeHistory;
import com.portal.enums.ChangeType;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignmentCompletionEffectBuilderTest {
    @Test void protectedApprovalCannotSkipTheExistingClaimRequirement() {
        var approval=mock(TaskApprovalCompletionComponent.class);
        var permissions=new TaskPermissionEvaluator(mock(com.portal.repository.DelegationRuleRepository.class),
                mock(com.portal.client.WorkflowEngineClient.class),mock(WorkspaceTaskFilterComponent.class),mock(AssignmentClaimReadAnnotator.class));
        when(approval.permissions()).thenReturn(permissions);
        var task=TaskInfo.builder().taskId("task").processInstanceId("instance").taskDefinitionKey("review")
                .bpmnAssigneeType("BU_ROLE").assignmentType("CANDIDATE_USERS").candidateUserIds(List.of("actor")).build();
        var request=TaskCompleteRequest.builder().taskId("task").action("APPROVE").build();
        var prepared=new TaskApprovalPreparation(task,request,"actor",Map.of(),Map.of(),Map.of(),false,null,List.of(),"frozen",null);
        when(approval.prepare(task,request,"actor",null,true)).thenReturn(prepared);
        var builder=new AssignmentCompletionEffectBuilder(approval,mock(ChangeHistoryComponent.class),
                mock(com.portal.repository.AssignmentCompletionBaselineRepository.class),mock(ActionPostEmailComponentImpl.class));
        assertThrows(com.portal.exception.PortalException.class,()->builder.prepare(task,request,"actor",null));
        verify(approval,never()).prepare(any(),any(),anyString(),nullable(String.class),eq(true));
        task.setAssignee("actor");
        assertEquals("actor",builder.prepare(task,request,"actor",null).actor());
    }
    @Test void originalSnapshotAndAuditAreFrozenBeforeAnyRetryOrLaterEngineValueExists() {
        var approval=mock(TaskApprovalCompletionComponent.class); var history=mock(ChangeHistoryComponent.class); var forms=mock(TaskFormComponent.class);
        when(approval.taskForms()).thenReturn(forms);
        var task=TaskInfo.builder().taskId("task").processInstanceId("instance").taskDefinitionKey("review").build();
        var prepared=new TaskApprovalPreparation(task,TaskCompleteRequest.builder().taskId("task").action("APPROVE").build(),"actor",
                Map.of("caption","accepted"),Map.of("caption","accepted"),Map.of("caption","accepted"),false,null,List.of(),"frozen",null);
        var before=JsonUtils.getObjectMapper().createObjectNode().put("caption","before");
        var after=before.deepCopy().put("caption","accepted"); after.putObject("_snapshot_task").put("caption","accepted");
        when(approval.portalValues(prepared)).thenReturn(new TaskApprovalPortalValues(before,after,"catalog"));
        when(forms.filterTaskSubmissionForChangeHistory(anyString(),anyString(),anyMap(),anyMap())).thenReturn(Map.of("caption","accepted"));
        when(forms.filterTaskFieldBaselineForChangeHistory(anyString(),anyString(),anyMap())).thenReturn(Map.of("caption","before"));
        var audit=ChangeHistory.builder().fieldName("caption").oldValue("before").newValue("accepted").changeType(ChangeType.FIELD_UPDATE)
                .timestamp(Instant.parse("2026-10-27T10:00:00Z")).batchId("original-batch").build();
        when(history.prepareFieldChanges(any(),anyMap(),anyMap())).thenReturn(List.of(audit));
        var baselines=mock(com.portal.repository.AssignmentCompletionBaselineRepository.class);
        when(baselines.capture("instance","task")).thenReturn(List.of(new AssignmentCompletionRequest.InstanceBaseline("instance","frozen",before)));
        var emails=mock(ActionPostEmailComponentImpl.class);
        when(emails.prepareCompletion(prepared.request())).thenReturn(java.util.Optional.of(new AssignmentPostAction.Plan("bound-action","Approve","APPROVE",
                "original",List.of(Map.of("enabled",true,"emailTo","${caption}")))));
        var effect=new AssignmentCompletionEffectBuilder(approval,history,baselines,emails).build(prepared,"a".repeat(64));
        after.put("caption","later"); after.withObject("/_snapshot_task").put("caption","later"); audit.setNewValue("later");
        var replayed=AssignmentVariablePatch.apply(before,effect.variablePatch());
        assertEquals("accepted",replayed.path("caption").asText()); assertEquals("accepted",replayed.path("_snapshot_task").path("caption").asText());
        assertEquals("accepted",effect.auditChanges().get(0).newValue()); assertEquals("original-batch",effect.auditChanges().get(0).batchId());
        assertEquals("a".repeat(64),effect.clientRequestDigest());
        assertEquals("bound-action",effect.postAction().get("actionId"));
        assertEquals("original",effect.postAction().get("comment"));
        verify(history,never()).recordFieldChanges(any(),anyMap(),anyMap());
    }
}
