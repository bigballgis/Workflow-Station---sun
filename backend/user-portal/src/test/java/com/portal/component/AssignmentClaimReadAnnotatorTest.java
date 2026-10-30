package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.assignment.AssignmentTaskReadSnapshot.*;
import com.portal.client.AssignmentTaskReadClient;
import com.portal.dto.TaskInfo;
import com.portal.repository.DelegationRuleRepository;
import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssignmentClaimReadAnnotatorTest {
    private final AssignmentTaskReadClient client=mock(AssignmentTaskReadClient.class);
    private final AssignmentClaimReadAnnotator annotator=new AssignmentClaimReadAnnotator(client);
    @Test void oneBatchVetoDoesNotGrantExistingPermissionOrChangeViewAccess() {
        var tasks=List.of(pool("allowed","actor"),pool("excluded","actor"),pool("outsider","someone"));
        when(client.read(anyList(),eq("actor"))).thenReturn(snapshot("actor",List.of(
                entry("allowed",true,State.CURRENT,true,Instant.now().plusSeconds(60)),
                entry("excluded",true,State.CURRENT,false,Instant.now().plusSeconds(60)),
                entry("outsider",true,State.CURRENT,true,Instant.now().plusSeconds(60)))));
        var evaluator=new TaskPermissionEvaluator(mock(DelegationRuleRepository.class),mock(WorkflowEngineClient.class),
                mock(WorkspaceTaskFilterComponent.class),annotator);
        evaluator.annotateClaimState(tasks,"actor",null);
        assertThat(tasks).extracting(TaskInfo::isClaimable).containsExactly(true,false,false);
        assertThat(evaluator.canViewTaskForm(tasks.get(1),"actor",null)).isTrue();
        verify(client).read(List.of("allowed","excluded","outsider"),"actor");
    }
    @Test void expiredUnknownAndPendingCannotClaimWhilePositiveLegacyKeepsExistingFlag() {
        var tasks=List.of(pool("expired","actor"),pool("unknown","actor"),pool("pending","actor"),pool("legacy","actor"));
        tasks.forEach(t->t.setClaimable(true));
        when(client.read(anyList(),anyString())).thenReturn(snapshot("actor",List.of(
                entry("expired",true,State.CURRENT,true,Instant.now().minusSeconds(1)),
                entry("unknown",null,State.LEGACY,true,null),entry("pending",true,State.SYNC_PENDING,true,Instant.now().plusSeconds(60)),
                entry("legacy",false,State.LEGACY,true,null))));
        annotator.annotate(tasks,"actor");
        assertThat(tasks).extracting(TaskInfo::isClaimable).containsExactly(false,false,false,true);
    }
    @Test void dependencyFailureDisablesPreviouslyCachedClaimFlag() {
        var task=pool("task","actor"); task.setClaimable(true);
        when(client.read(anyList(),anyString())).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE));
        annotator.annotate(List.of(task),"actor");
        assertThat(task.isClaimable()).isFalse();
        assertThat(task.getAssignmentClaimAccess().state()).isEqualTo(State.DEPENDENCY_UNAVAILABLE);
        assertThat(task.getCandidateUserIds()).containsExactly("actor");
    }
    @Test void wrongActorMissingDuplicateOrForeignEntriesCannotGrantClaim() {
        var good=entry("task",false,State.LEGACY,true,null);
        for (var response:List.of(snapshot("other",List.of(good)),snapshot("actor",List.of()),
                snapshot("actor",List.of(good,good)),snapshot("actor",List.of(entry("foreign",false,State.LEGACY,true,null))))) {
            var task=pool("task","actor"); task.setClaimable(true);
            when(client.read(anyList(),anyString())).thenReturn(response);
            annotator.annotate(List.of(task),"actor");
            assertThat(task.isClaimable()).isFalse();
        }
    }
    @Test void chunksLargeListsDeduplicatesRowsAndDoesNotPerformPerTaskHttp() {
        var tasks=new ArrayList<>(IntStream.range(0,1001).mapToObj(i->pool("task-"+i,"actor")).toList());
        tasks.add(pool("task-0","actor")); tasks.forEach(t->t.setClaimable(true));
        when(client.read(anyList(),eq("actor"))).thenAnswer(call->{
            List<String> ids=call.getArgument(0);
            assertThat(ids).hasSizeLessThanOrEqualTo(1000);
            return snapshot("actor",ids.stream().map(id->entry(id,false,State.LEGACY,true,null)).toList());
        });
        annotator.annotate(tasks,"actor");
        assertThat(tasks).allMatch(TaskInfo::isClaimable);
        verify(client,times(2)).read(anyList(),eq("actor"));
    }
    private TaskInfo pool(String id,String candidate) {
        return TaskInfo.builder().taskId(id).bpmnAssigneeType("BU_ROLE").assignmentType("CANDIDATE_USERS")
                .candidateUserIds(List.of(candidate)).build();
    }
    private Entry entry(String id,Boolean tracked,State state,boolean allowed,Instant deadline) {
        return new Entry(id,tracked,state,new Claim(allowed,"FIXED",null),new Stamp(Boolean.TRUE.equals(tracked)?1L:null,4,deadline));
    }
    private AssignmentTaskReadSnapshot snapshot(String actor,List<Entry> entries) {
        return new AssignmentTaskReadSnapshot(1,actor,4,Instant.now(),entries);
    }
}
