package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.assignment.AssignmentTaskReadSnapshot.*;
import com.platform.common.dto.UserPrincipal;
import com.portal.client.AssignmentTaskWriteReadClient;
import com.portal.dto.TaskInfo;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignmentTaskWriteReadAnnotatorTest {
    final AssignmentTaskWriteReadClient client=mock(AssignmentTaskWriteReadClient.class);
    final TaskPermissionEvaluator permissions=mock(TaskPermissionEvaluator.class);
    final AssignmentTaskWriteReadAnnotator annotator=new AssignmentTaskWriteReadAnnotator(client,permissions);
    @BeforeEach void actor() {
        var principal=UserPrincipal.builder().userId("actor").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void extraQualificationCannotGrantOriginalOperationPermission() {
        var task=task(); when(client.read("task","actor")).thenReturn(access(true,"CURRENT"));
        when(permissions.canProcessTask(eq(task),eq("actor"),nullable(String.class))).thenReturn(false);
        annotator.annotate(task,"actor");
        assertFalse(task.getAssignmentWriteAccess().canWrite());
        assertEquals("actor",task.getAssignee());
    }
    @Test void anExcludedOrUnavailableHolderRemainsViewableButCannotWrite() {
        for (String reason:List.of("ASSIGNMENT_EXCLUDED","ASSIGNMENT_DEPENDENCY_UNAVAILABLE")) {
            var task=task(); when(client.read("task","actor")).thenReturn(access(false,reason));
            annotator.annotate(task,"actor");
            assertFalse(task.getAssignmentWriteAccess().canWrite());
            assertEquals(reason,task.getAssignmentWriteAccess().evaluation().reason());
            assertEquals("actor",task.getAssignee());
        }
        verifyNoInteractions(permissions);
    }
    @Test void eligibleHeldTaskMayWriteWithoutBeingClaimableAgain() {
        var task=task(); task.setClaimable(false);
        when(client.read("task","actor")).thenReturn(access(true,"CURRENT"));
        when(permissions.canProcessTask(eq(task),eq("actor"),nullable(String.class))).thenReturn(true);
        annotator.annotate(task,"actor"); assertTrue(task.getAssignmentWriteAccess().canWrite());
    }
    @Test void failedReadAndUnverifiedActorDisableWriteWithoutRemovingTheViewData() {
        var task=task(); when(client.read("task","actor")).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE));
        annotator.annotate(task,"actor"); assertFalse(task.getAssignmentWriteAccess().canWrite());
        reset(client); SecurityContextHolder.clearContext();
        annotator.annotate(task,"actor"); assertFalse(task.getAssignmentWriteAccess().canWrite());
        assertEquals("actor",task.getAssignee()); verifyNoInteractions(client,permissions);
    }
    @Test void positivelyLegacyKeepsExistingUiBehaviorAndClosedTasksDoNotNeedAQualificationHttpCall() {
        var task=task(); task.setAssignmentClaimAccess(new Entry("task",false,State.LEGACY,new Claim(true,null,null),null));
        annotator.annotate(task,"actor"); assertNull(task.getAssignmentWriteAccess());
        task.setAssignmentClaimAccess(null); task.setStatus("COMPLETED");
        annotator.annotate(task,"actor"); assertFalse(task.getAssignmentWriteAccess().canWrite());
        verifyNoInteractions(client,permissions);
    }
    @Test void originalExplicitRoleClaimAndAutomaticCandidateClaimRemainDistinct() {
        var original=new TaskPermissionEvaluator(mock(com.portal.repository.DelegationRuleRepository.class),
                mock(com.portal.client.WorkflowEngineClient.class),mock(WorkspaceTaskFilterComponent.class),mock(AssignmentClaimReadAnnotator.class));
        var actual=new AssignmentTaskWriteReadAnnotator(client,original);
        when(client.read("task","actor")).thenReturn(access(true,"CURRENT"));
        var task=task(); task.setAssignee(null); task.setAssignmentType("CANDIDATE_USERS"); task.setCandidateUserIds(List.of("actor"));
        task.setBpmnAssigneeType("BU_ROLE"); actual.annotate(task,"actor");
        assertFalse(task.getAssignmentWriteAccess().canWrite());
        task.setBpmnAssigneeType("HIERARCHY_ROLE"); actual.annotate(task,"actor");
        assertTrue(task.getAssignmentWriteAccess().canWrite());
    }
    private TaskInfo task() { return TaskInfo.builder().taskId("task").assignee("actor").status("ACTIVE").build(); }
    private AssignmentTaskWriteReadSnapshot access(boolean allowed,String reason) {
        return new AssignmentTaskWriteReadSnapshot(1,true,allowed,reason,new Stamp(2L,4,Instant.now().plusSeconds(60)));
    }
}
