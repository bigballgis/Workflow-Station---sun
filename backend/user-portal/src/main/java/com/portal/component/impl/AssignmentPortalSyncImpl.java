package com.portal.component.impl;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import com.portal.component.AssignmentPortalSync;
import com.portal.component.AssignmentAcceptedProjection;
import com.portal.repository.AssignmentPortalSyncRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** No HTTP calls occur while holding this local transaction's shared case coordination lock. */
@Component
public class AssignmentPortalSyncImpl implements AssignmentPortalSync {
    private final AssignmentPortalSyncRepository repository;
    private final JdbcTemplate jdbc;
    private final AssignmentAcceptedProjection projection;
    private final com.portal.repository.AssignmentPostActionRepository postActions;
    private final com.portal.component.AssignmentCompletionCacheInvalidation caches;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entities;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private com.platform.common.assignment.AssignmentCoordinationVerifier coordination;
    public AssignmentPortalSyncImpl(AssignmentPortalSyncRepository repository, JdbcTemplate jdbc,AssignmentAcceptedProjection projection,
            com.portal.repository.AssignmentPostActionRepository postActions,com.portal.component.AssignmentCompletionCacheInvalidation caches) {
        this.repository = repository; this.jdbc = jdbc; this.projection=projection; this.postActions=postActions; this.caches=caches;
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public void apply(AssignmentOperationReceipt receipt) {
        if (receipt == null || receipt.acceptedEffect() == null || !("PENDING_SYNC".equals(receipt.state()) || "APPLIED".equals(receipt.state()))) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT);
        }
        coordination.requireVerified(); // The outside-transaction receipt transport must establish the local proof first.
        var fence = new AssignmentCaseFence(jdbc);
        fence.locked(receipt.caseId());
        entities.clear();
        String effectDigest = AssignmentRequestDigest.of(receipt.acceptedEffect());
        var applied = repository.applied(receipt.operationId());
        if (applied.isPresent()) {
            if (!applied.get().requestDigest().equals(receipt.requestDigest()) || !applied.get().effectDigest().equals(effectDigest)) {
                throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT);
            }
            return;
        }
        var effect = JsonUtils.getObjectMapper().convertValue(receipt.acceptedEffect(), AssignmentCompletionRequest.Effect.class);
        var current = repository.variables(effect.instanceId());
        var updated = AssignmentVariablePatch.apply(current.values(), effect.variablePatch());
        repository.replace(current, updated);
        repository.applyAudit(receipt,effect);
        projection.apply(receipt,effect);
        repository.record(receipt, effectDigest);
        postActions.record(receipt,effect);
        caches.schedule(receipt);
        fence.portalApplied(receipt.caseId(), receipt.operationSeq(), receipt.portalBaseRevision());
    }
}
