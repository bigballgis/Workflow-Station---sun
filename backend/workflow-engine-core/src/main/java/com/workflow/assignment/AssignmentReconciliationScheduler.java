package com.workflow.assignment;

import com.platform.common.assignment.AssignmentAuthorityFence;
import com.platform.common.assignment.AssignmentFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Database-owned due work survives restart; each tick bounds task count and elapsed preparation time. */
@Component
public class AssignmentReconciliationScheduler {
    private final JdbcTemplate jdbc;
    private final AssignmentTaskStateRepository states;
    private final AssignmentReconciliationComponent reconciliation;
    private final AssignmentReconciliationQueue queue;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AssignmentReconciliationScheduler.class);

    public AssignmentReconciliationScheduler(JdbcTemplate jdbc, AssignmentTaskStateRepository states,
            AssignmentReconciliationComponent reconciliation,AssignmentReconciliationQueue queue) {
        this.jdbc = jdbc; this.states = states; this.reconciliation = reconciliation; this.queue=queue;
    }

    @Scheduled(fixedDelay=1000, initialDelay=10000)
    public void refresh() {
        long started = System.nanoTime();
        int processed=0;
        for (String task : queue.due(new AssignmentAuthorityFence(jdbc).revision())) {
            try { reconciliation.reconcile(task); }
            catch (RuntimeException failure) {
                String reason = failure instanceof AssignmentFailure typed ? String.valueOf(typed.getDetails().get("reason"))
                        : "ASSIGNMENT_DEPENDENCY_UNAVAILABLE";
                states.find(task).ifPresent(state -> states.retryLater(state.caseId(), reason));
                LOG.warn("Assignment projection retry scheduled: taskId={}, reason={}", task, reason);
            }
            if (++processed>=100 || System.nanoTime() - started >= 1_500_000_000L) break;
        }
    }
}
