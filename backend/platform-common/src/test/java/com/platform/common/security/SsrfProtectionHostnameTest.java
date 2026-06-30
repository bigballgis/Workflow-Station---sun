package com.platform.common.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsrfProtectionHostnameTest {

    @Test
    void validateHostname_allowsPublicSmtpHost() {
        assertDoesNotThrow(() -> SsrfProtection.validateHostname("smtp.office365.com"));
    }

    @Test
    void validateHostname_blocksLoopbackLiteral() {
        assertThrows(SsrfProtection.SsrfException.class,
                () -> SsrfProtection.validateHostname("127.0.0.1"));
    }

    @Test
    void validateHostname_blocksMetadataEndpoint() {
        assertThrows(SsrfProtection.SsrfException.class,
                () -> SsrfProtection.validateHostname("169.254.169.254"));
    }

    @Test
    void validateHostname_allowsConfiguredInternalRelay() {
        assertDoesNotThrow(() -> SsrfProtection.validateHostname(
                "mail-relay.corp.local", Set.of("mail-relay.corp.local")));
    }

    @Test
    void validateHostname_rejectsUrlInjectionInHostField() {
        assertThrows(SsrfProtection.SsrfException.class,
                () -> SsrfProtection.validateHostname("http://evil.com"));
    }
}
