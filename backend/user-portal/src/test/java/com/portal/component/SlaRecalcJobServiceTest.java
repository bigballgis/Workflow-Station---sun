package com.portal.component;

import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SlaRecalcJobService}: per-case outcomes, optimistic-lock retry, supersede. JDBC is stubbed
 * per statement; the enricher is real, fed through the same JDBC seam.
 */
class SlaRecalcJobServiceTest {

    private static final String FU = "FU1";
    private static final String JOB = "job-1";

    private JdbcTemplate jdbc;
    private ProcessInstanceRepository repo;
    private SlaRecalcJobService service;
    private final Map<String, ProcessInstance> cases = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repo = mock(ProcessInstanceRepository.class);
        SlaDueDateEnricher enricher = new SlaDueDateEnricher(jdbc, new com.fasterxml.jackson.databind.ObjectMapper());
        service = new SlaRecalcJobService(jdbc, repo, enricher, mock(PlatformTransactionManager.class), Runnable::run);

        when(jdbc.query(contains("sla_config"), any(org.springframework.jdbc.core.RowMapper.class), eq(FU)))
                .thenReturn(List.of("{\"startDateSource\":\"FIELD\",\"startDateField\":\"received\",\"dueDateField\":\"due\"}"));
        when(jdbc.query(contains("SELECT lead_time_days FROM ac_sla_policies"), any(ResultSetExtractor.class), eq(FU)))
                .thenReturn(10);
        when(jdbc.query(contains("SELECT status FROM up_sla_recalc_jobs"), any(ResultSetExtractor.class), anyString()))
                .thenReturn("PENDING");
        when(jdbc.query(contains("SELECT version FROM ac_sla_policies"), any(ResultSetExtractor.class), eq(FU)))
                .thenReturn(3);
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Integer.class), eq(FU))).thenReturn(3);
        doReturn(1).when(jdbc).update(anyString(), any(Object[].class));
        // Each lookup returns a fresh copy, as a new transaction would; save writes it back.
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(cases.get((String) inv.getArgument(0)))
                .map(SlaRecalcJobServiceTest::copy));
        when(repo.save(any(ProcessInstance.class))).thenAnswer(inv -> store(inv.getArgument(0)));
    }

    @Test
    void recordsUpdatedAndSkippedCasesAndEndsPartial() {
        openCase("a", "2026-01-01", null);            // → 2026-01-11, UPDATED
        openCase("b", "2026-01-01", "2026-01-11");    // already right, UNCHANGED
        openCase("c", "garbage", "2025-01-01");       // start unparseable, SKIPPED
        pages(List.of("a", "b", "c"));

        service.run(JOB, FU);

        assertThat(cases.get("a").getVariables()).containsEntry("due", "2026-01-11");
        assertThat(cases.get("c").getVariables()).containsEntry("due", "2025-01-01");
        verify(repo, times(1)).save(any());
        List<Object[]> items = updatesContaining("INSERT INTO up_sla_recalc_job_items");
        assertThat(items).extracting(a -> a[2]).containsExactly("UPDATED", "SKIPPED");
        assertThat(items.get(0)).contains("2026-01-11");
        assertThat(updatesContaining("SET status = ?, updated_count")).singleElement()
                .satisfies(a -> assertThat(a[0]).isEqualTo("PARTIAL"));
        // The lead time is read once per job, not once per case (the enricher's own per-call read).
        verify(jdbc, times(1)).query(contains("SELECT lead_time_days FROM ac_sla_policies"),
                any(ResultSetExtractor.class), eq(FU));
        verify(jdbc, never()).query(contains("ac_sla_policies"),
                any(org.springframework.jdbc.core.RowMapper.class), eq(FU));
    }

    @Test
    void slowCasesHeartbeatInsideABatchNotOnlyAtItsEnd() {
        openCase("a", "2026-01-01", null);
        openCase("b", "2026-01-02", null);
        openCase("c", "2026-01-03", null);
        pages(List.of("a", "b", "c"));
        long[] now = {0};
        // Every case "takes" 40 s, longer than the 30 s heartbeat interval.
        SlaRecalcJobService slow = new SlaRecalcJobService(jdbc, repo,
                new SlaDueDateEnricher(jdbc, new com.fasterxml.jackson.databind.ObjectMapper()),
                mock(PlatformTransactionManager.class), Runnable::run,
                () -> now[0] += java.util.concurrent.TimeUnit.SECONDS.toNanos(40));

        slow.run(JOB, FU);

        // One beat after each case plus the end-of-batch beat; a per-batch heartbeat alone would be 1.
        assertThat(updatesContaining("heartbeat_at = CURRENT_TIMESTAMP, updated_count")).hasSize(4);
    }

    @Test
    void supersededMidBatchStopsAtTheNextHeartbeat() {
        openCase("a", "2026-01-01", null);
        openCase("b", "2026-01-02", null);
        pages(List.of("a", "b"));
        doReturn(0).when(jdbc).update(contains("heartbeat_at = CURRENT_TIMESTAMP, updated_count"), any(Object[].class));
        long[] now = {0};
        SlaRecalcJobService slow = new SlaRecalcJobService(jdbc, repo,
                new SlaDueDateEnricher(jdbc, new com.fasterxml.jackson.databind.ObjectMapper()),
                mock(PlatformTransactionManager.class), Runnable::run,
                () -> now[0] += java.util.concurrent.TimeUnit.SECONDS.toNanos(40));

        slow.run(JOB, FU);

        assertThat(cases.get("a").getVariables()).containsEntry("due", "2026-01-11");
        assertThat(cases.get("b").getVariables()).doesNotContainKey("due");
        assertThat(updatesContaining("SET status = ?, updated_count")).isEmpty();
    }

    @Test
    void failureWhileStartingEndsTheJobFailedInsteadOfLeavingItPending() {
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Integer.class), eq(FU)))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("statement timeout"));

        service.run(JOB, FU);

        assertThat(updatesContaining("SET status = ?, updated_count")).singleElement()
                .satisfies(a -> assertThat(a[0]).isEqualTo("FAILED"));
    }

    @Test
    void retriesOnceOnConcurrentUpdate() {
        openCase("a", "2026-01-01", null);
        pages(List.of("a"));
        when(repo.save(any(ProcessInstance.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(ProcessInstance.class, "a"))
                .thenAnswer(inv -> store(inv.getArgument(0)));

        service.run(JOB, FU);

        verify(repo, times(2)).save(any());
        assertThat(updatesContaining("SET status = ?, updated_count")).singleElement()
                .satisfies(a -> assertThat(a[0]).isEqualTo("SUCCEEDED"));
    }

    @Test
    void stopsWhenSupersededBetweenBatches() {
        openCase("a", "2026-01-01", null);
        pages(List.of("a"));
        doReturn(0).when(jdbc).update(contains("heartbeat_at = CURRENT_TIMESTAMP, updated_count"), any(Object[].class));

        service.run(JOB, FU);

        assertThat(updatesContaining("SET status = ?, updated_count")).isEmpty();
    }

    @Test
    void staleVersionIsRecordedSupersededWithoutRunning() {
        String jobId = service.submit(FU, 2, "u-1");

        assertThat(jobId).isNotBlank();
        List<Object[]> inserts = updatesContaining("INSERT INTO up_sla_recalc_jobs");
        assertThat(inserts).singleElement().satisfies(a -> assertThat(a[4]).isEqualTo("SUPERSEDED"));
        verify(repo, never()).findById(anyString());
    }

    @Test
    void currentVersionSupersedesOlderJobsAndRuns() {
        pages(List.of());

        service.submit(FU, 3, "u-1");

        assertThat(updatesContaining("SET status = 'SUPERSEDED'")).hasSize(1);
        verify(jdbc, atLeastOnce()).queryForObject(contains("COUNT(*)"), eq(Integer.class), eq(FU));
    }

    private void openCase(String id, String received, String due) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("received", received);
        if (due != null) {
            vars.put("due", due);
        }
        ProcessInstance pi = new ProcessInstance();
        pi.setId(id);
        pi.setFunctionUnitCode(FU);
        pi.setStatus("RUNNING");
        pi.setStartTime(LocalDateTime.of(2026, 1, 1, 9, 0));
        pi.setVariables(vars);
        cases.put(id, pi);
    }

    private ProcessInstance store(ProcessInstance pi) {
        cases.put(pi.getId(), pi);
        return pi;
    }

    private static ProcessInstance copy(ProcessInstance source) {
        ProcessInstance pi = new ProcessInstance();
        pi.setId(source.getId());
        pi.setFunctionUnitCode(source.getFunctionUnitCode());
        pi.setStatus(source.getStatus());
        pi.setStartTime(source.getStartTime());
        pi.setVariables(new HashMap<>(source.getVariables()));
        return pi;
    }

    private void pages(List<String> ids) {
        when(jdbc.queryForList(contains("SELECT id FROM up_process_instance"), eq(String.class), any(Object[].class)))
                .thenReturn(ids, List.of());
    }

    private List<Object[]> updatesContaining(String sqlFragment) {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), args.capture());
        List<Object[]> matches = new ArrayList<>();
        for (int i = 0; i < sql.getAllValues().size(); i++) {
            if (sql.getAllValues().get(i).contains(sqlFragment)) {
                matches.add(args.getAllValues().get(i));
            }
        }
        return matches;
    }
}
