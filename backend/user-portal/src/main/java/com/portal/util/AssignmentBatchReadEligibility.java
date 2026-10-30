package com.portal.util;

import com.platform.common.assignment.AssignmentTaskReadSnapshot;
import com.portal.dto.TaskInfo;
import java.time.Instant;
import java.util.Objects;

/** Extra read veto only. Existing batch permission and each row's live completion guard remain mandatory. */
public final class AssignmentBatchReadEligibility {
    private AssignmentBatchReadEligibility() { }
    public static boolean allows(TaskInfo task) {
        var access=task.getAssignmentClaimAccess();
        if (!bound(task,access)) return false;
        if (Boolean.FALSE.equals(access.tracked()) && access.state()==AssignmentTaskReadSnapshot.State.LEGACY) return true;
        if (!current(access)) return false;
        if (task.isClaimable()) return access.claim().exclusionAllowsClaim();
        var holding=access.claim().holding();
        if (holding==null || !Objects.equals(holder(task.getAssignee()),holder(holding.userId()))) return false;
        if (holder(holding.userId())==null) return access.claim().exclusionAllowsClaim();
        // Held-by-other rows never pass the list's open-candidate predicate. Explicit delegated writes
        // still need original delegation permission and the live actual/nominal-holder guard.
        return true;
    }
    private static boolean bound(TaskInfo task,AssignmentTaskReadSnapshot.Entry access) {
        return task.getTaskId()!=null && !task.getTaskId().isBlank() && access!=null && task.getTaskId().equals(access.taskId());
    }
    private static boolean current(AssignmentTaskReadSnapshot.Entry access) {
        return Boolean.TRUE.equals(access.tracked()) && access.state()==AssignmentTaskReadSnapshot.State.CURRENT
                && access.claim()!=null && access.stamp()!=null && access.stamp().caseRevision()!=null
                && access.stamp().caseRevision()>=0 && access.stamp().authorityRevision()>=0
                && access.stamp().validUntil()!=null && access.stamp().validUntil().isAfter(Instant.now());
    }
    private static String holder(String value) { return value==null || value.isBlank()?null:value; }
}
