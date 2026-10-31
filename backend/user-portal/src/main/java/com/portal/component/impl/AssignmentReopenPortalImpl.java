package com.portal.component.impl;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentReopenReceipt;
import com.portal.client.AssignmentReopenClient;
import com.portal.component.AssignmentReopenMirror;
import com.portal.component.AssignmentReopenPortal;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** HTTP orchestration has no outer database transaction. The mirror's short transaction owns its case lock. */
@Component
public class AssignmentReopenPortalImpl implements AssignmentReopenPortal {
    private final AssignmentReopenClient engine;
    private final AssignmentReopenMirror mirror;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AssignmentReopenPortalImpl.class);
    public AssignmentReopenPortalImpl(AssignmentReopenClient engine, AssignmentReopenMirror mirror) { this.engine = engine; this.mirror = mirror; }

    @Override public AssignmentReopenReceipt.Result reopen(String source, UUID operation, String actor) {
        if (actor == null || actor.isBlank()) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED);
        var receipt = engine.reopen(source, new AssignmentReopenReceipt.Request(operation, actor));
        try {
            mirror.apply(receipt);
            engine.mirrored(receipt);
            return new AssignmentReopenReceipt.Result(receipt.operationId(), receipt.processInstanceId(), receipt.processDefinitionId(), "READY");
        } catch (RuntimeException failure) {
            boolean conflict = failure instanceof AssignmentFailure typed && "ASSIGNMENT_SYNC_CONFLICT".equals(typed.getDetails().get("reason"));
            // FALLBACK(external): the new round is already durable; expose its pending/conflict state and let the persistent receipt recover it.
            LOG.warn("Accepted reopen awaiting Portal mirror: operationId={}, conflict={}", receipt.operationId(), conflict);
            return new AssignmentReopenReceipt.Result(receipt.operationId(), receipt.processInstanceId(), receipt.processDefinitionId(),
                    conflict ? "SYNC_CONFLICT" : "PENDING_SYNC");
        }
    }
}
