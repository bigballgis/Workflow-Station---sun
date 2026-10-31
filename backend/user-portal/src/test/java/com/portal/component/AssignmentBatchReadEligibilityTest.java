package com.portal.component;

import com.platform.common.assignment.AssignmentTaskReadSnapshot.*;
import com.portal.dto.TaskInfo;
import com.portal.util.BatchTaskEligibility;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentBatchReadEligibilityTest {
    @Test void heldRowsRequireCurrentQualificationAndTheActualCurrentHolder() {
        var task=task("actor"); task.setAssignmentClaimAccess(entry(State.CURRENT,false,new Holding("actor")));
        assertTrue(eligible(task));
        task.setAssignmentClaimAccess(entry(State.CURRENT,false,new Holding("other"))); assertFalse(eligible(task));
        task.setAssignmentClaimAccess(entry(State.CURRENT,false,new Holding(null))); assertFalse(eligible(task));
        task.setAssignmentClaimAccess(entry(State.RECONCILING,false,new Holding("actor"))); assertFalse(eligible(task));
    }
    @Test void openOrdinaryCandidatesNeedTheLatestExtraClaimVetoWhileBackupsMustBeClaimable() {
        var task=task(null); task.setCandidateUserIds(List.of("actor"));
        task.setAssignmentClaimAccess(entry(State.CURRENT,true,new Holding(null))); assertTrue(eligible(task));
        task.setAssignmentClaimAccess(entry(State.CURRENT,false,new Holding(null))); assertFalse(eligible(task));
        task.setClaimPoolTask(true); assertFalse(eligible(task));
        task.setClaimable(true); task.setAssignmentClaimAccess(entry(State.CURRENT,true,new Holding(null))); assertTrue(eligible(task));
    }
    @Test void unknownExpiredWrongTaskOrUnobservedHoldingIsNeverALegacyGrant() {
        var task=task("actor"); assertFalse(eligible(task));
        task.setAssignmentClaimAccess(entry(State.CURRENT,false,null)); assertFalse(eligible(task));
        var expired=entry(State.CURRENT,false,new Holding("actor"));
        task.setAssignmentClaimAccess(new Entry("task",true,State.CURRENT,expired.claim(),new Stamp(1L,0,Instant.now().minusSeconds(1))));
        assertFalse(eligible(task));
        task.setAssignmentClaimAccess(new Entry("other",true,State.CURRENT,expired.claim(),expired.stamp())); assertFalse(eligible(task));
    }
    @Test void positivelyLegacyKeepsItsExistingBatchPredicateAndDelegatesDoNotAppearAsOpenCandidates() {
        var task=task("actor"); task.setAssignmentClaimAccess(new Entry("task",false,State.LEGACY,new Claim(true,null,null),null));
        assertTrue(eligible(task));
        task.setAssignee("nominal"); task.setAssignmentClaimAccess(entry(State.CURRENT,false,new Holding("nominal")));
        assertFalse(eligible(task));
        assertTrue(BatchTaskEligibility.isEligible(task,"actor",row->true),"Explicit delegated writes retain original and live completion checks");
    }
    private boolean eligible(TaskInfo task) { return BatchTaskEligibility.isEligible(task,"actor",BatchTaskEligibility::isOpenCandidateTask); }
    private TaskInfo task(String holder) {
        return TaskInfo.builder().taskId("task").processDefinitionId("definition").taskDefinitionKey("node").allowBatch(true).assignee(holder).build();
    }
    private Entry entry(State state,boolean claim,Holding holding) {
        return new Entry("task",true,state,new Claim(claim,"PRIMARY",null,holding),new Stamp(1L,0,Instant.now().plusSeconds(60)));
    }
}
