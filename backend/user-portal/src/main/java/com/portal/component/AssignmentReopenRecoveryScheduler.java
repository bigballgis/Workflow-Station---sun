package com.portal.component;

import com.platform.common.assignment.AssignmentCaseFence;
import com.platform.common.assignment.AssignmentFailure;
import com.portal.client.AssignmentReopenClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AssignmentReopenRecoveryScheduler {
    private final AssignmentReopenClient engine;
    private final AssignmentReopenMirror mirror;
    private final JdbcTemplate jdbc;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AssignmentReopenRecoveryScheduler.class);
    public AssignmentReopenRecoveryScheduler(AssignmentReopenClient engine, AssignmentReopenMirror mirror, JdbcTemplate jdbc) {
        this.engine = engine; this.mirror = mirror; this.jdbc = jdbc;
    }
    @Scheduled(fixedDelay=1000, initialDelay=10000)
    public void recover() {
        if (!new AssignmentCaseFence(jdbc).hasPendingReopen()) return;
        long started = System.nanoTime();
        for (var receipt : engine.pending(50)) {
            try { mirror.apply(receipt); engine.mirrored(receipt); }
            catch (RuntimeException failure) {
                boolean conflict = failure instanceof AssignmentFailure typed && "ASSIGNMENT_SYNC_CONFLICT".equals(typed.getDetails().get("reason"));
                engine.retry(receipt, conflict);
                LOG.warn("Reopen mirror retry scheduled: operationId={}, conflict={}", receipt.operationId(), conflict);
            }
            if (System.nanoTime()-started>=1_500_000_000L) break;
        }
    }
}
