package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskInfo;
import com.portal.repository.DelegationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskPermissionEvaluator single-task delegatee")
class TaskPermissionEvaluatorSingleDelegateTest {

    @Mock
    private DelegationRuleRepository delegationRuleRepository;

    @Mock
    private WorkflowEngineClient workflowEngineClient;

    @Mock
    private WorkspaceTaskFilterComponent workspaceTaskFilterComponent;

    private TaskPermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new TaskPermissionEvaluator(
                com.portal.testsupport.PortalEntryStubs.delegationRules(delegationRuleRepository), workflowEngineClient, workspaceTaskFilterComponent,
                com.portal.testsupport.PortalEntryStubs.everyone());
    }

    @Test
    @DisplayName("USER delegatedTo can process without matching assignee")
    void userDelegateeCanProcess() {
        TaskInfo task = TaskInfo.builder()
                .taskId("t1")
                .assignmentType("USER")
                .assignee("user-a")
                .delegatedTargetType("USER")
                .delegatedTo("user-b")
                .build();
        assertTrue(evaluator.isSingleTaskDelegatee(task, "user-b", null));
        assertTrue(evaluator.canProcessTask(task, "user-b"));
        assertFalse(evaluator.isSingleTaskDelegatee(task, "user-c", null));
    }

    @Test
    @DisplayName("a delegate may process the task but not transfer or delegate it: only its holder can (D-60)")
    void aDelegateMayNotTransfer() {
        TaskInfo task = TaskInfo.builder()
                .taskId("t1")
                .processInstanceId("pi-1")
                .assignmentType("USER")
                .assignee("user-a")
                .delegatedTargetType("USER")
                .delegatedTo("user-b")
                .build();
        TaskInfo free = TaskInfo.builder().taskId("t2").processInstanceId("pi-2").assignmentType("CANDIDATE_USERS").build();

        evaluator.annotateClaimState(task, "user-b", null);
        assertTrue(evaluator.canProcessTask(task, "user-b"));
        assertFalse(task.isCanTransfer());
        assertFalse(task.isCanDelegate());
        evaluator.annotateClaimState(task, "user-a", null);
        assertTrue(task.isCanTransfer());
        assertTrue(task.isCanDelegate());
        evaluator.annotateClaimState(free, "user-c", null);
        assertTrue(free.isCanTransfer());
        assertTrue(free.isCanDelegate());
    }

    @Test
    @DisplayName("a delegate without Portal entry is no delegate until the entry is back (D-55)")
    void delegateWithoutEntryIsNoDelegate() {
        TaskInfo task = TaskInfo.builder()
                .taskId("t1")
                .assignmentType("USER")
                .assignee("user-a")
                .delegatorId("user-a")
                .delegatedTargetType("USER")
                .delegatedTo("user-b")
                .build();
        com.platform.security.service.PortalEntryService entry =
                org.mockito.Mockito.mock(com.platform.security.service.PortalEntryService.class);
        TaskPermissionEvaluator gated = new TaskPermissionEvaluator(
                new EffectiveDelegationRules(delegationRuleRepository, entry), workflowEngineClient,
                workspaceTaskFilterComponent, entry);

        when(entry.withPortalEntry(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(java.util.Set.of("user-a"));
        assertFalse(gated.isSingleTaskDelegatee(task, "user-b", null));
        assertFalse(gated.canProcessTask(task, "user-b"));
        assertTrue(gated.canProcessTask(task, "user-a"));

        when(entry.withPortalEntry(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(java.util.Set.of("user-a", "user-b"));
        assertTrue(gated.isSingleTaskDelegatee(task, "user-b", null));
    }

    @Test
    @DisplayName("BU_ROLE delegatee matches current workspace pair")
    void buRoleDelegateeMatchesWorkspace() {
        TaskInfo task = TaskInfo.builder()
                .taskId("t1")
                .assignmentType("USER")
                .assignee("user-a")
                .delegatedTargetType("BU_ROLE")
                .delegatedBuCode("HK")
                .delegatedRoleCode("APPROVER")
                .build();
        when(workspaceTaskFilterComponent.workspacePairMatches("HK", "APPROVER", "user-b"))
                .thenReturn(true);
        when(workspaceTaskFilterComponent.workspacePairMatches("HK", "APPROVER", "user-c"))
                .thenReturn(false);
        assertTrue(evaluator.isSingleTaskDelegatee(task, "user-b", null));
        assertTrue(evaluator.canProcessTask(task, "user-b"));
        assertFalse(evaluator.isSingleTaskDelegatee(task, "user-c", null));
        assertFalse(evaluator.canProcessTask(task, "user-c"));
    }
}
