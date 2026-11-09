package com.platform.security.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceTokenVerifierTest {

    @Test
    void onlyTheConfiguredTokenMatches() {
        ServiceTokenVerifier verifier = new ServiceTokenVerifier("svc-token");

        assertThat(verifier.matches("svc-token")).isTrue();
        assertThat(verifier.matches("svc-token2")).isFalse();
        assertThat(verifier.matches("")).isFalse();
        assertThat(verifier.matches(null)).isFalse();
    }

    @Test
    void aMissingTokenFailsAtStartup() {
        assertThatThrownBy(() -> new ServiceTokenVerifier(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SERVICE_INTERNAL_TOKEN");
    }
}
