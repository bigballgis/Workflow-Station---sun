package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.assignment.AssignmentTaskReadSnapshot.State;
import com.portal.client.AssignmentTaskWriteReadClient;
import com.portal.dto.*;
import com.portal.security.AssignmentPortalActor;
import com.platform.security.util.SecurityContextUtils;
import org.springframework.stereotype.Component;

/** Called after the independent view check. A qualification denial makes the displayed task read-only. */
@Component
public class AssignmentTaskWriteReadAnnotator {
    private final AssignmentTaskWriteReadClient client;
    private final TaskPermissionEvaluator permissions;
    public AssignmentTaskWriteReadAnnotator(AssignmentTaskWriteReadClient client,TaskPermissionEvaluator permissions) {
        this.client=client; this.permissions=permissions;
    }
    public void annotate(TaskInfo task,String actor) {
        var claim=task.getAssignmentClaimAccess();
        if (claim!=null && Boolean.FALSE.equals(claim.tracked()) && claim.state()==State.LEGACY) {
            task.setAssignmentWriteAccess(null); return; // Positively legacy cases retain their existing UI behavior.
        }
        try {
            AssignmentPortalActor.require(actor);
            if (TaskPermissionEvaluator.isTaskAlreadyClosedInEngineView(task)) {
                task.setAssignmentWriteAccess(new AssignmentTaskWriteAccess(false,null)); return;
            }
            var access=client.read(task.getTaskId(),actor);
            boolean canWrite=access.exclusionAllowsWrite() && permissions.canProcessTask(task,actor,
                    SecurityContextUtils.getCurrentUsername().orElse(null));
            task.setAssignmentWriteAccess(new AssignmentTaskWriteAccess(canWrite,access));
        } catch (AssignmentFailure failure) {
            // FALLBACK(ux): keep the authorized view; never infer a right to write from a failed qualification read.
            var access=new AssignmentTaskWriteReadSnapshot(1,null,false,(String)failure.getDetails().get("reason"),null);
            task.setAssignmentWriteAccess(new AssignmentTaskWriteAccess(false,access));
        }
    }
}
