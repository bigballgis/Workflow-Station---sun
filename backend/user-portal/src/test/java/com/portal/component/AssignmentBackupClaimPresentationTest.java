package com.portal.component;

import com.platform.common.assignment.AssignmentTaskReadSnapshot;
import com.platform.common.assignment.AssignmentTaskReadSnapshot.*;
import com.portal.client.*;
import com.portal.dto.TaskInfo;
import com.portal.repository.DelegationRuleRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignmentBackupClaimPresentationTest {
    @Test void qualifiedBackupOnANonBuRoleNodeMustBeOfferedAnExplicitClaim() {
        var reads=mock(AssignmentTaskReadClient.class);
        var entry=new Entry("backup",true,State.CURRENT,new Claim(true,"BACKUP",null),
                new Stamp(1L,4,Instant.now().plusSeconds(60)));
        when(reads.read(anyList(),eq("actor"))).thenReturn(new AssignmentTaskReadSnapshot(1,"actor",4,Instant.now(),List.of(entry)));
        var evaluator=new TaskPermissionEvaluator(mock(DelegationRuleRepository.class),mock(WorkflowEngineClient.class),
                mock(WorkspaceTaskFilterComponent.class),new AssignmentClaimReadAnnotator(reads));
        var task=TaskInfo.builder().taskId("backup").bpmnAssigneeType("HIERARCHY_ROLE")
                .assignmentType("CANDIDATE_USERS").candidateUserIds(List.of("actor")).build();
        evaluator.annotateClaimState(task,"actor",null);
        assertTrue(task.isClaimPoolTask(),"Verified backup participation starts with an explicit claim even outside BU Role nodes");
        assertTrue(task.isClaimable());
        assertFalse(task.isClaimedByCurrentUser());
    }
    @Test void backupClaimDoesNotBypassCandidateMembershipOrTheExclusionVeto() {
        assertFalse(annotate(List.of("someone"),null,entry(true,"BACKUP",null,true)).isClaimable());
        assertFalse(annotate(List.of("actor"),null,entry(false,"BACKUP",null,true)).isClaimable());
    }
    @Test void heldBackupKeepsItsReleasePresentationWhenPrimaryCandidatesRecover() {
        var task=annotate(List.of("actor"),"actor",entry(false,"PRIMARY","BACKUP",true));
        assertTrue(task.isClaimPoolTask()); assertTrue(task.isClaimedByCurrentUser()); assertFalse(task.isClaimable());
    }
    @Test void legacySourceLabelsCannotTurnAnOrdinaryTaskIntoTheNewBackupPool() {
        var task=annotate(List.of("actor"),null,entry(true,"BACKUP",null,false));
        assertFalse(task.isClaimPoolTask()); assertFalse(task.isClaimable());
    }
    private Entry entry(boolean allowed,String poolSource,String holderSource,boolean tracked) {
        return new Entry("backup",tracked,tracked?State.CURRENT:State.LEGACY,new Claim(allowed,poolSource,holderSource),
                new Stamp(tracked?1L:null,4,Instant.now().plusSeconds(60)));
    }
    private TaskInfo annotate(List<String> candidates,String holder,Entry entry) {
        var reads=mock(AssignmentTaskReadClient.class);
        when(reads.read(anyList(),eq("actor"))).thenReturn(new AssignmentTaskReadSnapshot(1,"actor",4,Instant.now(),List.of(entry)));
        var evaluator=new TaskPermissionEvaluator(mock(DelegationRuleRepository.class),mock(WorkflowEngineClient.class),
                mock(WorkspaceTaskFilterComponent.class),new AssignmentClaimReadAnnotator(reads));
        var task=TaskInfo.builder().taskId("backup").bpmnAssigneeType("HIERARCHY_ROLE").assignee(holder)
                .assignmentType("CANDIDATE_USERS").candidateUserIds(candidates).build();
        evaluator.annotateClaimState(task,"actor",null); return task;
    }
}
