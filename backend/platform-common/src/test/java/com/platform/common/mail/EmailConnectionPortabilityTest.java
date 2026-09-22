package com.platform.common.mail;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailConnectionPortabilityTest {

    @Test
    void readPasswordEnvKey_readsBoundVariable() {
        assertEquals("email.qq.inbound.password",
                EmailConnectionPortability.readPasswordEnvKey(Map.of(
                        "passwordEnvKey", "email.qq.inbound.password",
                        "credentialEncrypted", "should-ignore")));
    }

    @Test
    void readPasswordEnvKey_ignoresLegacyCiphertext() {
        assertNull(EmailConnectionPortability.readPasswordEnvKey(
                Map.of("credentialEncrypted", "enc-new")));
    }
}
