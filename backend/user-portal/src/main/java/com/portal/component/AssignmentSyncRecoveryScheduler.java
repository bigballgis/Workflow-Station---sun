package com.portal.component;

import com.platform.common.assignment.AssignmentCaseFence;
import com.platform.common.assignment.AssignmentFailure;
import com.portal.client.AssignmentOperationClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Applies only immutable Engine receipts, without re-completing tasks or reauthorizing their former actors. */
@Component
public class AssignmentSyncRecoveryScheduler {
    private final AssignmentOperationClient engine;
    private final AssignmentPortalSync sync;
    private final JdbcTemplate jdbc;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AssignmentSyncRecoveryScheduler.class);

    public AssignmentSyncRecoveryScheduler(AssignmentOperationClient engine, AssignmentPortalSync sync, JdbcTemplate jdbc) {
        this.engine = engine; this.sync = sync; this.jdbc = jdbc;
    }

    @Scheduled(fixedDelay=1000, initialDelay=10000)
    public void recover() {
        if (!new AssignmentCaseFence(jdbc).hasPendingSynchronization()) return;
        long started = System.nanoTime();
        for (var receipt : engine.pending(50)) {
            try {
                sync.apply(receipt);
                engine.confirm(receipt);
            } catch (RuntimeException failure) {
                boolean conflict = failure instanceof AssignmentFailure typed
                        && java.util.Set.of("ASSIGNMENT_SYNC_CONFLICT", "ASSIGNMENT_OPERATION_CONFLICT").contains(typed.getDetails().get("reason"));
                engine.retry(receipt, conflict);
                LOG.warn("Assignment synchronization deferred: operationId={}, conflict={}", receipt.operationId(), conflict);
            }
            if (System.nanoTime() - started >= 1_500_000_000L) break;
        }
    }
}
