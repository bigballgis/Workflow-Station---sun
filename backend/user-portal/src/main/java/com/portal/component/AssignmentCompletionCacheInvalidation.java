package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.LinkedHashSet;

/** Recovery must evict the same read caches as interactive completion, only after the local commit succeeds. */
@Component
public class AssignmentCompletionCacheInvalidation {
    private final MineTaskListCache mine;
    private final MiOverlayComponent overlays;
    public AssignmentCompletionCacheInvalidation(MineTaskListCache mine,MiOverlayComponent overlays) { this.mine=mine; this.overlays=overlays; }
    public void schedule(AssignmentOperationReceipt receipt) {
        var states=JsonUtils.getObjectMapper().valueToTree(receipt.acceptedEffect().get(AssignmentAcceptedProcessState.EFFECT_KEY));
        if (!states.isArray() || states.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) throw conflict();
        var instances=new LinkedHashSet<String>();
        for (var state:states) {
            var id=state.get("instanceId");
            if (id==null || !id.isTextual() || id.asText().isBlank() || !instances.add(id.asText())) throw conflict();
        }
        if (!instances.contains(receipt.acceptedEffect().get("instanceId"))) throw conflict();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                mine.invalidate(); instances.forEach(overlays::invalidate);
            }
        });
    }
    private AssignmentFailure conflict() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT); }
}
