package com.workflow.email.inbound;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailMonitorDeliveryRetryTest {

    @Test
    void countsConsecutiveFailuresPerMessage() {
        EmailMonitorDeliveryRetry retry = new EmailMonitorDeliveryRetry();

        assertThat(retry.recordFailure("rule-1", "m1")).isEqualTo(1);
        assertThat(retry.recordFailure("rule-1", "m1")).isEqualTo(2);
        assertThat(retry.attempts("rule-1", "m1")).isEqualTo(2);
    }

    @Test
    void countsAreIsolatedPerRuleAndPerMessage() {
        EmailMonitorDeliveryRetry retry = new EmailMonitorDeliveryRetry();

        retry.recordFailure("rule-1", "m1");
        retry.recordFailure("rule-1", "m1");

        assertThat(retry.attempts("rule-1", "m2")).isZero();
        assertThat(retry.attempts("rule-2", "m1")).isZero();
        assertThat(retry.recordFailure("rule-2", "m1")).isEqualTo(1);
    }

    @Test
    void clearReleasesTheCountSoALaterFailureStartsOver() {
        EmailMonitorDeliveryRetry retry = new EmailMonitorDeliveryRetry();
        retry.recordFailure("rule-1", "m1");
        retry.recordFailure("rule-1", "m1");

        retry.clear("rule-1", "m1");

        assertThat(retry.attempts("rule-1", "m1")).isZero();
        assertThat(retry.recordFailure("rule-1", "m1")).isEqualTo(1);
    }

    @Test
    void keysDoNotCollideWhenRuleAndMessageIdsRunTogether() {
        EmailMonitorDeliveryRetry retry = new EmailMonitorDeliveryRetry();

        retry.recordFailure("rule", "1m1");

        assertThat(retry.attempts("rule1", "m1")).isZero();
    }
}
