package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.BatchCompleteRequest;
import com.portal.dto.BatchCompleteResponse;
import com.portal.dto.BatchTaskGroup;
import com.portal.dto.TaskActionInfo;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import com.portal.service.TaskActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchTaskComponentTest {

    private static final String PD = "purchase:3:abc";
    /** Catalog row of release 1.0.23 that the requests were started on. */
    private static final String CATALOG_1023 = "cat-1023";

    @Mock private TodoListQueryComponent todoListQueryComponent;
    @Mock private RequestIdEnricher requestIdEnricher;
    @Mock private TaskActionService taskActionService;
    @Mock private WorkflowEngineClient workflowEngineClient;
    @Mock private TaskProcessComponent taskProcessComponent;
    @Mock private ActionPostEmailComponent actionPostEmailComponent;
    @Mock private MineTaskListCache mineTaskListCache;

    private BatchTaskComponent component;

    private final TaskActionInfo approve = TaskActionInfo.builder().actionId("a-approve").actionName("a-approve")
            .actionType("APPROVE").icon("check").buttonColor("#2E7D32").build();
    private final TaskActionInfo processReject = action("a-reject", "PROCESS_REJECT", "{\"requireComment\":true}");
    private final TaskActionInfo transfer = action("a-transfer", "TRANSFER", null);

    @BeforeEach
    void setUp() {
        component = new BatchTaskComponent(todoListQueryComponent, requestIdEnricher, taskActionService,
                workflowEngineClient, taskProcessComponent, actionPostEmailComponent, mineTaskListCache);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(actionPostEmailComponent).runWithPostEmail(any(), any(Runnable.class));
        when(taskActionService.getTaskActionsFromPayload(anyMap()))
                .thenReturn(List.of(approve, processReject, transfer));
        when(requestIdEnricher.loadCatalogVersions(any())).thenReturn(Map.of(CATALOG_1023, "1.0.23"));
    }

    @Test
    void completesEligibleTasksWithMappedActionAndNoFormData() {
        engineTask("t1", "u1", true);
        engineTask("t2", "u1", true);

        BatchCompleteResponse result = component.complete("u1", "alice",
                new BatchCompleteRequest(List.of("t1", "t2"), "a-approve", null));

        assertThat(result.succeeded()).containsExactly("t1", "t2");
        assertThat(result.failed()).isEmpty();
        ArgumentCaptor<TaskCompleteRequest> captor = ArgumentCaptor.forClass(TaskCompleteRequest.class);
        verify(taskProcessComponent, times(2)).completeTask(captor.capture(), eq("u1"), eq("alice"));
        TaskCompleteRequest sent = captor.getAllValues().get(0);
        assertThat(sent.getAction()).isEqualTo("APPROVE");
        assertThat(sent.getActionId()).isEqualTo("a-approve");
        assertThat(sent.getFormData()).isNull();
        assertThat(sent.getVariables()).isNull();
        verify(mineTaskListCache).invalidate();
    }

    @Test
    void nothingCompletedLeavesTheListCacheAlone() {
        engineTask("t1", "u2", true);

        component.complete("u1", "alice", new BatchCompleteRequest(List.of("t1"), "a-approve", null));

        verify(mineTaskListCache, never()).invalidate();
    }

    @Test
    void groupActionsComeFromAnOpenTaskWhenTheFirstListedOneIsAlreadyClosed() {
        when(todoListQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(
                task("closed", PD, "dept", "u1", true),
                task("open", PD, "dept", "u1", true)));
        Map<String, Object> closed = new HashMap<>();
        closed.put("taskId", "closed");
        closed.put("status", "COMPLETED");
        when(workflowEngineClient.getTaskById("closed")).thenReturn(Optional.of(Map.of("data", closed)));
        engineTask("open", "u1", true);

        List<BatchTaskGroup> groups = component.listGroups("u1");

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).actions()).extracting(BatchTaskGroup.BatchTaskAction::actionId)
                .containsExactly("a-approve", "a-reject");
        verify(taskActionService).getTaskActionsFromPayload(org.mockito.ArgumentMatchers.argThat(
                m -> "open".equals(m.get("taskId"))));
    }

    @Test
    void aGroupWhosePinnedReleaseCannotResolveItsActionsIsLeftOutWithoutFailingTheOthers() {
        // A release deployed before runtime versioning has no action source ids, so its actions do not resolve.
        when(todoListQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(
                task("legacy", PD, "dept", "u1", true),
                task("ok", PD, "finance", "u1", true)));
        engineTask("legacy", "u1", true);
        engineTask("ok", "u1", true);
        when(taskActionService.getTaskActionsFromPayload(argThat(m -> "legacy".equals(m.get("taskId")))))
                .thenThrow(new com.platform.common.functionunit.FunctionUnitVersionException(
                        "VERSION_SNAPSHOT_INCOMPLETE", CATALOG_1023));

        List<BatchTaskGroup> groups = component.listGroups("u1");

        assertThat(groups).extracting(BatchTaskGroup::taskDefinitionKey).containsExactly("finance");
    }

    @Test
    void sharedCommentIsRequiredAndWrittenToEveryTask() {
        engineTask("t1", "u1", true);

        BatchCompleteResponse missing = component.complete("u1", "alice",
                new BatchCompleteRequest(List.of("t1"), "a-reject", "  "));
        assertThat(missing.failed()).extracting(BatchCompleteResponse.Failure::reason)
                .containsExactly("Comment is required");
        verify(taskProcessComponent, never()).completeTask(any(), any(), any());

        BatchCompleteResponse ok = component.complete("u1", "alice",
                new BatchCompleteRequest(List.of("t1"), "a-reject", " looks wrong "));
        assertThat(ok.succeeded()).containsExactly("t1");
        ArgumentCaptor<TaskCompleteRequest> captor = ArgumentCaptor.forClass(TaskCompleteRequest.class);
        verify(taskProcessComponent).completeTask(captor.capture(), eq("u1"), eq("alice"));
        assertThat(captor.getValue().getAction()).isEqualTo("REJECT");
        assertThat(captor.getValue().getComment()).isEqualTo("looks wrong");
    }

    @Test
    void ineligibleTasksFailIndividuallyWithoutStoppingOthers() {
        engineTask("not-batch-node", "u1", false);
        engineTask("someone-else", "u2", true);
        engineTask("ok", "u1", true);
        when(workflowEngineClient.getTaskById("gone")).thenReturn(Optional.empty());

        BatchCompleteResponse result = component.complete("u1", "alice", new BatchCompleteRequest(
                List.of("not-batch-node", "someone-else", "gone", "ok"), "a-approve", null));

        assertThat(result.succeeded()).containsExactly("ok");
        assertThat(result.failed()).extracting(BatchCompleteResponse.Failure::taskId)
                .containsExactly("not-batch-node", "someone-else", "gone");
        verify(taskProcessComponent, times(1)).completeTask(any(), any(), any());
    }

    @Test
    void unclaimedPoolTaskIsClaimedForMeThenCompleted() {
        engineTask("pool", null, true);
        claimableForMe("pool");

        BatchCompleteResponse result = component.complete("u1", "alice",
                new BatchCompleteRequest(List.of("pool"), "a-approve", null));

        assertThat(result.succeeded()).containsExactly("pool");
        InOrder order = inOrder(taskProcessComponent);
        order.verify(taskProcessComponent).claimTask("pool", "u1", "alice");
        order.verify(taskProcessComponent).completeTask(any(), eq("u1"), eq("alice"));
    }

    @Test
    void poolTaskAColleagueClaimedMeanwhileFailsAloneAndIsNotCompleted() {
        engineTask("taken", null, true);
        engineTask("ok", "u1", true);
        claimableForMe("taken");
        when(taskProcessComponent.claimTask("taken", "u1", "alice"))
                .thenThrow(new PortalException("403", "You are not allowed to claim this task"));

        BatchCompleteResponse result = component.complete("u1", "alice",
                new BatchCompleteRequest(List.of("taken", "ok"), "a-approve", null));

        assertThat(result.succeeded()).containsExactly("ok");
        assertThat(result.failed()).extracting(BatchCompleteResponse.Failure::taskId).containsExactly("taken");
        verify(taskProcessComponent, times(1)).completeTask(any(), any(), any());
    }

    @Test
    void candidatePoolTaskIMayProcessIsCompletedAndLeftToTheSingleSubmitClaim() {
        engineTask("candidate", null, true);
        when(taskProcessComponent.canProcessTask(argThat(t -> "candidate".equals(t.getTaskId())), eq("u1"), eq("alice")))
                .thenReturn(true);

        BatchCompleteResponse result = component.complete("u1", "alice",
                new BatchCompleteRequest(List.of("candidate"), "a-approve", null));

        assertThat(result.succeeded()).containsExactly("candidate");
        verify(taskProcessComponent, never()).claimTask(any(), any(), any());
        verify(taskProcessComponent).completeTask(any(), eq("u1"), eq("alice"));
    }

    @Test
    void poolTaskIsNotClaimedWhenTheBatchRequestIsInvalid() {
        engineTask("pool", null, true);
        claimableForMe("pool");

        component.complete("u1", "alice", new BatchCompleteRequest(List.of("pool"), "a-reject", null));

        verify(taskProcessComponent, never()).claimTask(any(), any(), any());
    }

    @Test
    void alreadyCompletedTaskReportsSo() {
        Map<String, Object> done = new HashMap<>();
        done.put("taskId", "done");
        done.put("status", "COMPLETED");
        when(workflowEngineClient.getTaskById("done")).thenReturn(Optional.of(Map.of("data", done)));

        BatchCompleteResponse result = component.complete("u1", "alice",
                new BatchCompleteRequest(List.of("done"), "a-approve", null));

        assertThat(result.failed()).extracting(BatchCompleteResponse.Failure::reason)
                .containsExactly("Task already completed");
        verify(taskProcessComponent, never()).completeTask(any(), any(), any());
    }

    @Test
    void rejectsActionsNotOnNodeOrNotBatchable() {
        engineTask("t1", "u1", true);

        BatchCompleteResponse notOnNode = component.complete("u1", "alice",
                new BatchCompleteRequest(List.of("t1"), "a-unknown", null));
        BatchCompleteResponse notBatchable = component.complete("u1", "alice",
                new BatchCompleteRequest(List.of("t1"), "a-transfer", null));

        assertThat(notOnNode.failed()).hasSize(1);
        assertThat(notBatchable.failed()).hasSize(1);
        verify(taskProcessComponent, never()).completeTask(any(), any(), any());
    }

    @Test
    void groupsEligibleTasksByProcessVersionAndNodeWithBatchActionsOnly() {
        when(todoListQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(
                task("t1", PD, "dept", "u1", true),
                task("t2", PD, "dept", "u1", true),
                task("t3", PD, "finance", "u1", true),
                task("pool-not-mine", PD, "dept", null, true),
                claimable(task("pool-claimable", PD, "dept", null, true)),
                candidates(task("candidate-pool", PD, "dept", null, true), "u1", "u2"),
                task("no-flag", PD, "dept", "u1", false)));
        engineTask("t1", "u1", true);
        engineTask("t3", "u1", true);

        List<BatchTaskGroup> groups = component.listGroups("u1");

        assertThat(groups).extracting(BatchTaskGroup::taskDefinitionKey, BatchTaskGroup::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("dept", 4),
                        org.assertj.core.groups.Tuple.tuple("finance", 1));
        BatchTaskGroup dept = groups.get(0);
        assertThat(dept.version()).isEqualTo("1.0.23");
        assertThat(dept.groupKey()).isEqualTo("fu:" + CATALOG_1023);
        assertThat(dept.actions()).extracting(BatchTaskGroup.BatchTaskAction::actionId)
                .containsExactly("a-approve", "a-reject");
        assertThat(dept.actions().get(1).requireComment()).isTrue();
        // Presentation travels as configured in DW so the batch bar renders the task-page buttons.
        assertThat(dept.actions().get(0).icon()).isEqualTo("check");
        assertThat(dept.actions().get(0).buttonColor()).isEqualTo("#2E7D32");
        // The raw configJson travels too, so the portal applies Allowed Roles as the task page does.
        assertThat(dept.actions().get(1).configJson()).isEqualTo("{\"requireComment\":true}");
        // Groups trust To Do membership; the remote-backed canProcessTask is for the submit only.
        verify(taskProcessComponent, never()).canProcessTask(any(), any(), any());
    }

    @Test
    void nodesInsideOneMultiInstanceSubProcessAreNamedByTaskNotByTheSharedStep() {
        TaskInfo first = task("t1", PD, "sub_form1", "u1", true);
        TaskInfo second = task("t2", PD, "sub_form2", "u1", true);
        first.setCurrentStepName("multi");
        second.setCurrentStepName("multi");
        when(todoListQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(first, second));
        engineTask("t1", "u1", true);
        engineTask("t2", "u1", true);

        assertThat(component.listGroups("u1")).extracting(BatchTaskGroup::nodeName)
                .containsExactly("sub_form1", "sub_form2");
    }

    @Test
    void oneReleaseSpreadOverSeveralEngineDefinitionsIsOneGroupLabelledWithTheReleaseVersion() {
        // 1.0.23 was deployed to the engine three times (v5, v6, v8): users see one release.
        when(todoListQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(
                task("t5", "purchase:5:a", "dept", "u1", true),
                task("t6", "purchase:6:b", "dept", "u1", true),
                task("t8", "purchase:8:c", "dept", "u1", true)));
        engineTask("t5", "u1", true);

        List<BatchTaskGroup> groups = component.listGroups("u1");

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).count()).isEqualTo(3);
        assertThat(groups.get(0).version()).isEqualTo("1.0.23");
    }

    @Test
    void requestWithoutAPinnedReleaseGroupsByEngineDefinitionWithoutAVersion() {
        TaskInfo unpinned = task("t9", PD, "dept", "u1", true);
        unpinned.setFunctionUnitCatalogId(null);
        when(todoListQueryComponent.listMergedTodoTasks("u1")).thenReturn(List.of(unpinned));
        engineTask("t9", "u1", true);

        List<BatchTaskGroup> groups = component.listGroups("u1");

        assertThat(groups).singleElement().satisfies(g -> {
            assertThat(g.groupKey()).isEqualTo("pd:" + PD);
            assertThat(g.version()).isNull();
        });
    }

    private void engineTask(String taskId, String assignee, boolean allowBatch) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("processDefinitionId", PD);
        payload.put("taskDefinitionKey", "dept");
        payload.put("currentAssignee", assignee);
        payload.put("allowBatch", allowBatch);
        payload.put("actionIds", List.of("a-approve", "a-reject", "a-transfer"));
        when(workflowEngineClient.getTaskById(taskId)).thenReturn(Optional.of(Map.of("data", payload)));
    }

    /** What TaskPermissionEvaluator#annotateClaimState marks on a free BU Role pool task I am a candidate for. */
    private void claimableForMe(String taskId) {
        doAnswer(inv -> {
            TaskInfo t = inv.getArgument(0);
            t.setClaimable(taskId.equals(t.getTaskId()));
            return null;
        }).when(taskProcessComponent).annotateClaimState(any(TaskInfo.class), eq("u1"), eq("alice"));
    }

    private static TaskInfo candidates(TaskInfo task, String... userIds) {
        task.setCandidateUserIds(List.of(userIds));
        return task;
    }

    private static TaskInfo claimable(TaskInfo task) {
        task.setClaimPoolTask(true);
        task.setClaimable(true);
        return task;
    }

    private static TaskInfo task(String id, String pd, String node, String assignee, boolean allowBatch) {
        return TaskInfo.builder()
                .taskId(id)
                .functionUnitCatalogId(CATALOG_1023)
                .processDefinitionId(pd)
                .processDefinitionKey("purchase")
                .taskDefinitionKey(node)
                .taskName(node)
                .assignee(assignee)
                .allowBatch(allowBatch)
                .build();
    }

    private static TaskActionInfo action(String id, String type, String configJson) {
        return TaskActionInfo.builder().actionId(id).actionName(id).actionType(type).configJson(configJson).build();
    }
}
