package com.workflow.component;

import com.workflow.entity.N8nExecutionRecord;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-Based Tests for N8nTimeoutChecker
 *
 * Feature: n8n-workflow-integration, Property 11: 超时检测正确性
 *
 * For any RUNNING execution record:
 * - If now > startedAt + timeoutSeconds, isTimedOut should return true
 * - If now <= startedAt + timeoutSeconds, isTimedOut should return false
 *
 * N8nTimeoutChecker is instantiated with null dependencies since
 * isTimedOut() doesn't use them.
 *
 * Validates: Requirements 6.3
 */
class N8nTimeoutCheckerPropertyTest {

    private N8nTimeoutChecker createChecker() {
        return new N8nTimeoutChecker(null, null);
    }

    /**
     * Feature: n8n-workflow-integration, Property 11: 超时检测正确性
     *
     * When now > startedAt + timeoutSeconds, isTimedOut should return true.
     *
     * Validates: Requirements 6.3
     */
    @Property(tries = 100)
    @Label("Property 11: isTimedOut returns true when now is past deadline")
    void isTimedOutReturnsTrueWhenPastDeadline(
            @ForAll("randomStartedAt") Instant startedAt,
            @ForAll @IntRange(min = 1, max = 86400) int timeoutSeconds,
            @ForAll @IntRange(min = 1, max = 3600) int secondsPastDeadline) {

        N8nTimeoutChecker checker = createChecker();

        Instant deadline = startedAt.plusSeconds(timeoutSeconds);
        Instant now = deadline.plusSeconds(secondsPastDeadline);

        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setStatus("RUNNING");
        record.setStartedAt(startedAt);
        record.setTimeoutSeconds(timeoutSeconds);

        assertThat(checker.isTimedOut(record, now))
                .as("Should be timed out when now (%s) > startedAt (%s) + timeoutSeconds (%d) = deadline (%s)",
                        now, startedAt, timeoutSeconds, deadline)
                .isTrue();
    }

    /**
     * Feature: n8n-workflow-integration, Property 11: 超时检测正确性
     *
     * When now <= startedAt + timeoutSeconds, isTimedOut should return false.
     *
     * Validates: Requirements 6.3
     */
    @Property(tries = 100)
    @Label("Property 11: isTimedOut returns false when now is before or at deadline")
    void isTimedOutReturnsFalseWhenBeforeOrAtDeadline(
            @ForAll("randomStartedAt") Instant startedAt,
            @ForAll @IntRange(min = 1, max = 86400) int timeoutSeconds,
            @ForAll @IntRange(min = 0, max = 86400) int secondsBeforeDeadline) {

        // Ensure secondsBeforeDeadline <= timeoutSeconds so now <= deadline
        int effectiveOffset = secondsBeforeDeadline % (timeoutSeconds + 1);

        N8nTimeoutChecker checker = createChecker();

        Instant now = startedAt.plusSeconds(effectiveOffset);
        Instant deadline = startedAt.plusSeconds(timeoutSeconds);

        // now should be <= deadline
        assertThat(now.isAfter(deadline)).isFalse();

        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setStatus("RUNNING");
        record.setStartedAt(startedAt);
        record.setTimeoutSeconds(timeoutSeconds);

        assertThat(checker.isTimedOut(record, now))
                .as("Should NOT be timed out when now (%s) <= deadline (%s)",
                        now, deadline)
                .isFalse();
    }

    /**
     * Feature: n8n-workflow-integration, Property 11: 超时检测正确性
     *
     * When startedAt is null, isTimedOut should return false (safe default).
     *
     * Validates: Requirements 6.3
     */
    @Property(tries = 100)
    @Label("Property 11: isTimedOut returns false when startedAt is null")
    void isTimedOutReturnsFalseWhenStartedAtIsNull(
            @ForAll @IntRange(min = 1, max = 86400) int timeoutSeconds) {

        N8nTimeoutChecker checker = createChecker();

        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setStatus("RUNNING");
        record.setStartedAt(null);
        record.setTimeoutSeconds(timeoutSeconds);

        assertThat(checker.isTimedOut(record, Instant.now())).isFalse();
    }

    /**
     * Feature: n8n-workflow-integration, Property 11: 超时检测正确性
     *
     * When timeoutSeconds is null, isTimedOut should return false (safe default).
     *
     * Validates: Requirements 6.3
     */
    @Property(tries = 100)
    @Label("Property 11: isTimedOut returns false when timeoutSeconds is null")
    void isTimedOutReturnsFalseWhenTimeoutSecondsIsNull(
            @ForAll("randomStartedAt") Instant startedAt) {

        N8nTimeoutChecker checker = createChecker();

        N8nExecutionRecord record = new N8nExecutionRecord();
        record.setStatus("RUNNING");
        record.setStartedAt(startedAt);
        record.setTimeoutSeconds(null);

        assertThat(checker.isTimedOut(record, Instant.now())).isFalse();
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<Instant> randomStartedAt() {
        // Generate instants within a reasonable range (2020-01-01 to 2025-12-31)
        long minEpoch = Instant.parse("2020-01-01T00:00:00Z").getEpochSecond();
        long maxEpoch = Instant.parse("2025-12-31T23:59:59Z").getEpochSecond();
        return Arbitraries.longs().between(minEpoch, maxEpoch)
                .map(Instant::ofEpochSecond);
    }
}
