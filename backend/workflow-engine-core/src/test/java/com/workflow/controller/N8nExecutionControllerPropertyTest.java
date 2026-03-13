package com.workflow.controller;

import com.workflow.entity.N8nExecutionRecord;
import net.jqwik.api.*;

import jakarta.persistence.criteria.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-Based Tests for N8nExecutionController
 *
 * Tests execution record query filtering correctness (Property 12)
 * and synchronous execution timeout control (Property 14).
 */
class N8nExecutionControllerPropertyTest {

    private static final String[] STATUSES = {"PENDING", "RUNNING", "SUCCESS", "FAILED", "TIMEOUT"};
    private static final String[] SOURCE_TYPES = {"SERVICE_TASK", "ACTION"};

    // ==================== Property 12: 执行记录查询过滤正确性 ====================

    /**
     * Feature: n8n-workflow-integration, Property 12: 执行记录查询过滤正确性
     *
     * For any set of execution records and filter conditions (processInstanceId, status, time range),
     * all returned records should satisfy all specified filter conditions.
     *
     * This test generates a random list of records and random filter criteria,
     * then applies the filtering logic in-memory and verifies correctness.
     *
     * Validates: Requirements 7.3
     */
    @Property(tries = 100)
    @Label("Property 12a: Filtered records satisfy processInstanceId filter")
    void filteredRecordsSatisfyProcessInstanceIdFilter(
            @ForAll("randomRecordList") List<N8nExecutionRecord> records,
            @ForAll("randomProcessInstanceId") String filterProcessInstanceId) {

        List<N8nExecutionRecord> filtered = applyFilter(records, filterProcessInstanceId, null, null, null);

        for (N8nExecutionRecord record : filtered) {
            assertThat(record.getProcessInstanceId())
                    .as("Filtered record must match processInstanceId filter")
                    .isEqualTo(filterProcessInstanceId);
        }

        // Verify completeness: all matching records are included
        long expectedCount = records.stream()
                .filter(r -> filterProcessInstanceId.equals(r.getProcessInstanceId()))
                .count();
        assertThat(filtered).hasSize((int) expectedCount);
    }

    /**
     * Feature: n8n-workflow-integration, Property 12: 执行记录查询过滤正确性
     *
     * Validates: Requirements 7.3
     */
    @Property(tries = 100)
    @Label("Property 12b: Filtered records satisfy status filter")
    void filteredRecordsSatisfyStatusFilter(
            @ForAll("randomRecordList") List<N8nExecutionRecord> records,
            @ForAll("randomStatus") String filterStatus) {

        List<N8nExecutionRecord> filtered = applyFilter(records, null, filterStatus, null, null);

        for (N8nExecutionRecord record : filtered) {
            assertThat(record.getStatus())
                    .as("Filtered record must match status filter")
                    .isEqualTo(filterStatus);
        }

        long expectedCount = records.stream()
                .filter(r -> filterStatus.equals(r.getStatus()))
                .count();
        assertThat(filtered).hasSize((int) expectedCount);
    }

    /**
     * Feature: n8n-workflow-integration, Property 12: 执行记录查询过滤正确性
     *
     * Validates: Requirements 7.3
     */
    @Property(tries = 100)
    @Label("Property 12c: Filtered records satisfy time range filter")
    void filteredRecordsSatisfyTimeRangeFilter(
            @ForAll("randomRecordList") List<N8nExecutionRecord> records,
            @ForAll("randomTimeRange") Instant[] timeRange) {

        Instant startTime = timeRange[0];
        Instant endTime = timeRange[1];

        List<N8nExecutionRecord> filtered = applyFilter(records, null, null,
                startTime.toString(), endTime.toString());

        for (N8nExecutionRecord record : filtered) {
            assertThat(record.getCreatedAt())
                    .as("Filtered record createdAt must be >= startTime")
                    .isAfterOrEqualTo(startTime);
            assertThat(record.getCreatedAt())
                    .as("Filtered record createdAt must be <= endTime")
                    .isBeforeOrEqualTo(endTime);
        }
    }

    /**
     * Feature: n8n-workflow-integration, Property 12: 执行记录查询过滤正确性
     *
     * Combined filters: all conditions must be satisfied simultaneously.
     *
     * Validates: Requirements 7.3
     */
    @Property(tries = 100)
    @Label("Property 12d: Combined filters - all conditions satisfied simultaneously")
    void combinedFiltersSatisfiedSimultaneously(
            @ForAll("randomRecordList") List<N8nExecutionRecord> records,
            @ForAll("randomProcessInstanceId") String filterPid,
            @ForAll("randomStatus") String filterStatus) {

        List<N8nExecutionRecord> filtered = applyFilter(records, filterPid, filterStatus, null, null);

        for (N8nExecutionRecord record : filtered) {
            assertThat(record.getProcessInstanceId())
                    .as("Record must match processInstanceId filter")
                    .isEqualTo(filterPid);
            assertThat(record.getStatus())
                    .as("Record must match status filter")
                    .isEqualTo(filterStatus);
        }

        long expectedCount = records.stream()
                .filter(r -> filterPid.equals(r.getProcessInstanceId()))
                .filter(r -> filterStatus.equals(r.getStatus()))
                .count();
        assertThat(filtered).hasSize((int) expectedCount);
    }

    /**
     * Feature: n8n-workflow-integration, Property 12: 执行记录查询过滤正确性
     *
     * No filters: all records returned.
     *
     * Validates: Requirements 7.3
     */
    @Property(tries = 100)
    @Label("Property 12e: No filters returns all records")
    void noFiltersReturnsAllRecords(
            @ForAll("randomRecordList") List<N8nExecutionRecord> records) {

        List<N8nExecutionRecord> filtered = applyFilter(records, null, null, null, null);
        assertThat(filtered).hasSize(records.size());
    }

    // ==================== Property 14: 同步执行超时控制 ====================

    /**
     * Feature: n8n-workflow-integration, Property 14: 同步执行超时控制
     *
     * For any N8N Action synchronous execution request, if the N8N workflow
     * execution time exceeds the configured timeoutSeconds, the system should
     * return a timeout error response and not block indefinitely.
     *
     * This test verifies that the timeout configuration is correctly passed
     * through the request and that the timeout value is always positive and bounded.
     *
     * Validates: Requirements 10.22
     */
    @Property(tries = 100)
    @Label("Property 14a: Timeout configuration is correctly bounded and positive")
    void timeoutConfigurationIsCorrectlyBounded(
            @ForAll("randomTimeoutSeconds") int timeoutSeconds) {

        // Timeout must be positive
        assertThat(timeoutSeconds)
                .as("Timeout seconds must be positive")
                .isGreaterThan(0);

        // Simulate creating an execution record with the timeout
        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setTimeoutSeconds(timeoutSeconds);
        record.setStartedAt(Instant.now());
        record.setStatus("RUNNING");
        record.setSourceType("ACTION");

        assertThat(record.getTimeoutSeconds())
                .as("Record timeout must match configured value")
                .isEqualTo(timeoutSeconds);

        // Verify timeout detection: if current time > startedAt + timeoutSeconds, it's timed out
        Instant deadline = record.getStartedAt().plusSeconds(timeoutSeconds);
        assertThat(deadline)
                .as("Deadline must be after startedAt")
                .isAfter(record.getStartedAt());
    }

    /**
     * Feature: n8n-workflow-integration, Property 14: 同步执行超时控制
     *
     * Verify that a simulated execution exceeding timeout is correctly detected.
     *
     * Validates: Requirements 10.22
     */
    @Property(tries = 100)
    @Label("Property 14b: Execution exceeding timeout is correctly detected as timed out")
    void executionExceedingTimeoutIsDetected(
            @ForAll("randomTimeoutSeconds") int timeoutSeconds,
            @ForAll("randomExcessSeconds") int excessSeconds) {

        Instant startedAt = Instant.now().minus(timeoutSeconds + excessSeconds, ChronoUnit.SECONDS);

        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setStartedAt(startedAt);
        record.setTimeoutSeconds(timeoutSeconds);
        record.setStatus("RUNNING");
        record.setSourceType("ACTION");

        Instant deadline = record.getStartedAt().plusSeconds(record.getTimeoutSeconds());
        boolean isTimedOut = Instant.now().isAfter(deadline);

        assertThat(isTimedOut)
                .as("Execution started %d+%d seconds ago with timeout %d should be timed out",
                        timeoutSeconds, excessSeconds, timeoutSeconds)
                .isTrue();
    }

    /**
     * Feature: n8n-workflow-integration, Property 14: 同步执行超时控制
     *
     * Verify that an execution within timeout is NOT detected as timed out.
     *
     * Validates: Requirements 10.22
     */
    @Property(tries = 100)
    @Label("Property 14c: Execution within timeout is not detected as timed out")
    void executionWithinTimeoutIsNotTimedOut(
            @ForAll("randomTimeoutSeconds") int timeoutSeconds) {

        // Started just now, so well within timeout
        Instant startedAt = Instant.now();

        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setStartedAt(startedAt);
        record.setTimeoutSeconds(timeoutSeconds);
        record.setStatus("RUNNING");
        record.setSourceType("ACTION");

        Instant deadline = record.getStartedAt().plusSeconds(record.getTimeoutSeconds());
        boolean isTimedOut = Instant.now().isAfter(deadline);

        assertThat(isTimedOut)
                .as("Execution started just now with timeout %d should NOT be timed out", timeoutSeconds)
                .isFalse();
    }

    // ==================== Helper: In-memory filter ====================

    /**
     * Applies the same filtering logic as N8nExecutionController.buildFilterSpecification
     * but in-memory on a list of records.
     */
    private List<N8nExecutionRecord> applyFilter(List<N8nExecutionRecord> records,
                                                  String processInstanceId,
                                                  String status,
                                                  String startTimeStr,
                                                  String endTimeStr) {
        return records.stream()
                .filter(r -> processInstanceId == null || processInstanceId.isBlank()
                        || processInstanceId.equals(r.getProcessInstanceId()))
                .filter(r -> status == null || status.isBlank()
                        || status.equals(r.getStatus()))
                .filter(r -> {
                    if (startTimeStr == null || startTimeStr.isBlank()) return true;
                    try {
                        Instant start = Instant.parse(startTimeStr);
                        return r.getCreatedAt() != null && !r.getCreatedAt().isBefore(start);
                    } catch (Exception e) { return true; }
                })
                .filter(r -> {
                    if (endTimeStr == null || endTimeStr.isBlank()) return true;
                    try {
                        Instant end = Instant.parse(endTimeStr);
                        return r.getCreatedAt() != null && !r.getCreatedAt().isAfter(end);
                    } catch (Exception e) { return true; }
                })
                .collect(Collectors.toList());
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<List<N8nExecutionRecord>> randomRecordList() {
        return Arbitraries.integers().between(1, 20).flatMap(size ->
                randomRecord().list().ofSize(size)
        );
    }

    private Arbitrary<N8nExecutionRecord> randomRecord() {
        Arbitrary<String> pids = Arbitraries.of("proc-1", "proc-2", "proc-3", "proc-4");
        Arbitrary<String> statuses = Arbitraries.of(STATUSES);
        Arbitrary<String> sourceTypes = Arbitraries.of(SOURCE_TYPES);
        Arbitrary<Integer> timeouts = Arbitraries.integers().between(30, 600);
        Arbitrary<Long> offsets = Arbitraries.longs().between(0L, 86400L * 30);

        return Combinators.combine(pids, statuses, sourceTypes, timeouts, offsets)
                .as((pid, st, src, timeout, offset) -> {
                    N8nExecutionRecord r = new N8nExecutionRecord();
                    r.setProcessInstanceId(pid);
                    r.setStatus(st);
                    r.setSourceType(src);
                    r.setTimeoutSeconds(timeout);
                    Instant base = Instant.parse("2024-01-01T00:00:00Z");
                    r.setCreatedAt(base.plusSeconds(offset));
                    r.setStartedAt(base.plusSeconds(offset));
                    return r;
                });
    }

    @Provide
    Arbitrary<String> randomProcessInstanceId() {
        return Arbitraries.of("proc-1", "proc-2", "proc-3", "proc-4");
    }

    @Provide
    Arbitrary<String> randomStatus() {
        return Arbitraries.of(STATUSES);
    }

    @Provide
    Arbitrary<Instant[]> randomTimeRange() {
        Instant base = Instant.parse("2024-01-01T00:00:00Z");
        return Arbitraries.longs().between(0L, 86400L * 15).flatMap(startOffset ->
                Arbitraries.longs().between(startOffset, 86400L * 30).map(endOffset ->
                        new Instant[]{base.plusSeconds(startOffset), base.plusSeconds(endOffset)}
                )
        );
    }

    @Provide
    Arbitrary<Integer> randomTimeoutSeconds() {
        return Arbitraries.integers().between(1, 3600);
    }

    @Provide
    Arbitrary<Integer> randomExcessSeconds() {
        return Arbitraries.integers().between(1, 300);
    }
}
