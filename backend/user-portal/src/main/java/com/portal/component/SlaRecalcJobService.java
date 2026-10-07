package com.portal.component;

import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Re-stamps the SLA due date of every open case (RUNNING / SUSPENDED) of one Function Unit after its
 * lead time changed in Admin Center.
 *
 * <p>Each case is written in its own transaction under the entity's optimistic lock, so a user
 * saving the same case concurrently costs one retry, not the whole job. Only UPDATED / SKIPPED /
 * FAILED cases are stored as items; unchanged ones are counted. A newer job for the same Function
 * Unit marks older unfinished ones SUPERSEDED; a running job notices between batches and stops.
 * Jobs run on one dedicated thread, so recalculations never compete with request threads.
 */
@Slf4j
@Component
public class SlaRecalcJobService {

    static final int BATCH_SIZE = 200;
    private static final int QUEUE_CAPACITY = 50;
    private static final int RETENTION_DAYS = 90;
    private static final int STALE_HEARTBEAT_MINUTES = 5;
    /**
     * Heartbeat at least this often, not just once per batch: a slow batch must never let the heartbeat
     * age past {@link #STALE_HEARTBEAT_MINUTES}, or a restarting replica would fail a live job.
     */
    static final long HEARTBEAT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final JdbcTemplate jdbcTemplate;
    private final ProcessInstanceRepository processInstanceRepository;
    private final SlaDueDateEnricher enricher;
    private final TransactionTemplate perCaseTx;
    private final Executor executor;
    private final LongSupplier nanoTime;

    @Autowired
    public SlaRecalcJobService(JdbcTemplate jdbcTemplate,
                               ProcessInstanceRepository processInstanceRepository,
                               SlaDueDateEnricher enricher,
                               PlatformTransactionManager transactionManager) {
        this(jdbcTemplate, processInstanceRepository, enricher, transactionManager, new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY), r -> {
                    Thread t = new Thread(r, "sla-recalc");
                    t.setDaemon(true);
                    return t;
                }));
    }

    /** Tests pass a synchronous executor. */
    SlaRecalcJobService(JdbcTemplate jdbcTemplate,
                        ProcessInstanceRepository processInstanceRepository,
                        SlaDueDateEnricher enricher,
                        PlatformTransactionManager transactionManager,
                        Executor executor) {
        this(jdbcTemplate, processInstanceRepository, enricher, transactionManager, executor, System::nanoTime);
    }

    /** Tests also control the clock that paces heartbeats. */
    SlaRecalcJobService(JdbcTemplate jdbcTemplate,
                        ProcessInstanceRepository processInstanceRepository,
                        SlaDueDateEnricher enricher,
                        PlatformTransactionManager transactionManager,
                        Executor executor,
                        LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        this.jdbcTemplate = jdbcTemplate;
        this.processInstanceRepository = processInstanceRepository;
        this.enricher = enricher;
        this.perCaseTx = new TransactionTemplate(transactionManager);
        this.perCaseTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executor = executor;
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }

    /**
     * Portal instances that died mid-job leave rows PENDING/RUNNING forever; fail those whose
     * heartbeat went stale so Admin shows them as failed and offers a retrigger.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void failStaleJobs() {
        int failed = jdbcTemplate.update("""
                UPDATE up_sla_recalc_jobs
                SET status = 'FAILED', error_message = 'Interrupted: the portal instance running it stopped',
                    finished_at = CURRENT_TIMESTAMP
                WHERE status IN ('PENDING', 'RUNNING')
                  AND COALESCE(heartbeat_at, submitted_at) < CURRENT_TIMESTAMP - make_interval(mins => ?)
                """, STALE_HEARTBEAT_MINUTES);
        if (failed > 0) {
            log.warn("Marked {} stale SLA recalculation job(s) FAILED", failed);
        }
    }

    /**
     * Records a PENDING job and queues it. A request for an already superseded policy version is
     * recorded SUPERSEDED without running.
     */
    public String submit(String functionUnitCode, Integer policyVersion, String triggeredBy) {
        purgeExpired();
        String jobId = UUID.randomUUID().toString();
        Integer currentVersion = currentPolicyVersion(functionUnitCode);
        boolean stale = currentVersion == null || (policyVersion != null && policyVersion < currentVersion);
        jdbcTemplate.update("""
                UPDATE up_sla_recalc_jobs SET status = 'SUPERSEDED', finished_at = CURRENT_TIMESTAMP
                WHERE function_unit_code = ? AND status IN ('PENDING', 'RUNNING')
                """, functionUnitCode);
        String staleReason = !stale ? null : currentVersion == null
                ? "No SLA lead time is set for " + functionUnitCode
                : "Policy version " + policyVersion + " is no longer current (now " + currentVersion + ")";
        jdbcTemplate.update("""
                INSERT INTO up_sla_recalc_jobs (id, function_unit_code, policy_version, triggered_by, status,
                    error_message, finished_at)
                VALUES (?, ?, ?, ?, ?, ?, CASE WHEN ? THEN CURRENT_TIMESTAMP END)
                """, jobId, functionUnitCode, policyVersion, triggeredBy,
                stale ? "SUPERSEDED" : "PENDING", staleReason, stale);
        if (!stale) {
            try {
                executor.execute(() -> run(jobId, functionUnitCode));
            } catch (RejectedExecutionException e) {
                jdbcTemplate.update("""
                        UPDATE up_sla_recalc_jobs SET status = 'FAILED', finished_at = CURRENT_TIMESTAMP,
                            error_message = 'Recalculation queue is full; retry later'
                        WHERE id = ?
                        """, jobId);
                log.warn("SLA recalculation queue full; job {} for {} not started", jobId, functionUnitCode);
            }
        }
        return jobId;
    }

    void run(String jobId, String functionUnitCode) {
        if (!"PENDING".equals(status(jobId))) {
            return;
        }
        Counts counts = new Counts();
        // Everything after the status check is inside the try: a failure while starting must end the
        // job as FAILED, not leave it PENDING until some portal instance restarts.
        try {
            // Read once for the whole job; per-case reads would cost one query per case.
            Integer leadTimeDays = jdbcTemplate.query(
                    "SELECT lead_time_days FROM ac_sla_policies WHERE function_unit_code = ?",
                    rs -> rs.next() ? rs.getInt(1) : null, functionUnitCode);
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM up_process_instance WHERE function_unit_code = ? AND status IN ('RUNNING', 'SUSPENDED')",
                    Integer.class, functionUnitCode);
            jdbcTemplate.update("""
                    UPDATE up_sla_recalc_jobs SET status = 'RUNNING', lead_time_days = ?, total_count = ?,
                        started_at = CURRENT_TIMESTAMP, heartbeat_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'PENDING'
                    """, leadTimeDays, total, jobId);
            String lastId = "";
            long lastBeat = nanoTime.getAsLong();
            while (true) {
                List<String> ids = jdbcTemplate.queryForList("""
                        SELECT id FROM up_process_instance
                        WHERE function_unit_code = ? AND status IN ('RUNNING', 'SUSPENDED') AND id > ?
                        ORDER BY id LIMIT ?
                        """, String.class, functionUnitCode, lastId, BATCH_SIZE);
                if (ids.isEmpty()) {
                    break;
                }
                for (String id : ids) {
                    recalculateCase(jobId, id, leadTimeDays, counts);
                    if (nanoTime.getAsLong() - lastBeat >= HEARTBEAT_INTERVAL_NANOS) {
                        if (!heartbeat(jobId, counts)) {
                            log.info("SLA recalculation job {} superseded after {} case(s)", jobId, counts.processed());
                            return;
                        }
                        lastBeat = nanoTime.getAsLong();
                    }
                }
                lastId = ids.get(ids.size() - 1);
                if (!heartbeat(jobId, counts)) {
                    log.info("SLA recalculation job {} superseded after {} case(s)", jobId, counts.processed());
                    return;
                }
                lastBeat = nanoTime.getAsLong();
            }
            String finalStatus = counts.failed == 0 && counts.skipped == 0 ? "SUCCEEDED" : "PARTIAL";
            finish(jobId, finalStatus, counts, null);
            log.info("SLA recalculation job {} for {} finished {}: updated={} unchanged={} skipped={} failed={}",
                    jobId, functionUnitCode, finalStatus, counts.updated, counts.unchanged, counts.skipped, counts.failed);
        } catch (RuntimeException e) {
            log.error("SLA recalculation job {} for {} failed", jobId, functionUnitCode, e);
            finish(jobId, "FAILED", counts, truncate(e.getMessage()));
        }
    }

    private void recalculateCase(String jobId, String processInstanceId, Integer leadTimeDays, Counts counts) {
        for (int attempt = 1; ; attempt++) {
            try {
                SlaDueDateEnricher.Result result = perCaseTx.execute(status -> restamp(processInstanceId, leadTimeDays));
                record(jobId, processInstanceId, result, counts);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt < 2) {
                    continue;
                }
                counts.failed++;
                insertItem(jobId, processInstanceId, "FAILED", null, null, "Concurrent update: " + e.getMessage());
                return;
            } catch (RuntimeException e) {
                counts.failed++;
                insertItem(jobId, processInstanceId, "FAILED", null, null, e.getClass().getSimpleName() + ": " + e.getMessage());
                return;
            }
        }
    }

    /** Runs inside the per-case transaction; returns null when the case left the open states meanwhile. */
    private SlaDueDateEnricher.Result restamp(String processInstanceId, Integer leadTimeDays) {
        ProcessInstance pi = processInstanceRepository.findById(processInstanceId).orElse(null);
        if (pi == null || !("RUNNING".equals(pi.getStatus()) || "SUSPENDED".equals(pi.getStatus()))) {
            return null;
        }
        Map<String, Object> vars = pi.getVariables() == null ? new HashMap<>() : new HashMap<>(pi.getVariables());
        SlaDueDateEnricher.Result result = enricher.compute(pi.getFunctionUnitCode(), vars,
                pi.getStartTime() == null ? null : pi.getStartTime().toLocalDate(), leadTimeDays);
        if (result.kind() == SlaDueDateEnricher.Result.Kind.COMPUTED
                && !result.newDueDate().equals(result.oldDueDate())) {
            // New map instance: Hibernate's JSON dirty check compares by reference.
            vars.put(result.dueDateField(), result.newDueDate());
            pi.setVariables(vars);
            processInstanceRepository.save(pi);
        }
        return result;
    }

    private void record(String jobId, String processInstanceId, SlaDueDateEnricher.Result result, Counts counts) {
        if (result == null) {
            counts.unchanged++;
            return;
        }
        switch (result.kind()) {
            case COMPUTED -> {
                if (result.newDueDate().equals(result.oldDueDate())) {
                    counts.unchanged++;
                } else {
                    counts.updated++;
                    insertItem(jobId, processInstanceId, "UPDATED", result.oldDueDate(), result.newDueDate(), null);
                }
            }
            case START_MISSING, NO_LEAD_TIME, NOT_CONFIGURED -> {
                counts.skipped++;
                insertItem(jobId, processInstanceId, "SKIPPED", result.oldDueDate(), null,
                        result.reason() != null ? result.reason() : "SLA due date mapping is not configured");
            }
        }
    }

    private void insertItem(String jobId, String processInstanceId, String outcome,
                            String oldDue, String newDue, String reason) {
        jdbcTemplate.update("""
                INSERT INTO up_sla_recalc_job_items (job_id, process_instance_id, outcome, old_due_date, new_due_date, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, jobId, processInstanceId, outcome, truncate32(oldDue), truncate32(newDue), truncate(reason));
    }

    /** @return false when the job was superseded and must stop */
    private boolean heartbeat(String jobId, Counts counts) {
        return jdbcTemplate.update("""
                UPDATE up_sla_recalc_jobs SET heartbeat_at = CURRENT_TIMESTAMP, updated_count = ?,
                    unchanged_count = ?, skipped_count = ?, failed_count = ?
                WHERE id = ? AND status = 'RUNNING'
                """, counts.updated, counts.unchanged, counts.skipped, counts.failed, jobId) > 0;
    }

    private void finish(String jobId, String status, Counts counts, String error) {
        jdbcTemplate.update("""
                UPDATE up_sla_recalc_jobs SET status = ?, updated_count = ?, unchanged_count = ?, skipped_count = ?,
                    failed_count = ?, error_message = ?, heartbeat_at = CURRENT_TIMESTAMP, finished_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('PENDING', 'RUNNING')
                """, status, counts.updated, counts.unchanged, counts.skipped, counts.failed, error, jobId);
    }

    private String status(String jobId) {
        return jdbcTemplate.query("SELECT status FROM up_sla_recalc_jobs WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, jobId);
    }

    private Integer currentPolicyVersion(String functionUnitCode) {
        return jdbcTemplate.query("SELECT version FROM ac_sla_policies WHERE function_unit_code = ?",
                rs -> rs.next() ? rs.getInt(1) : null, functionUnitCode);
    }

    private void purgeExpired() {
        jdbcTemplate.update(
                "DELETE FROM up_sla_recalc_jobs WHERE finished_at < CURRENT_TIMESTAMP - make_interval(days => ?)",
                RETENTION_DAYS);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 1000);
    }

    private static String truncate32(String s) {
        return s == null || s.length() <= 32 ? s : s.substring(0, 32);
    }

    private static final class Counts {
        int updated;
        int unchanged;
        int skipped;
        int failed;

        int processed() {
            return updated + unchanged + skipped + failed;
        }
    }
}
