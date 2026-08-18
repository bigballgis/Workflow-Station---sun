package com.admin.component;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemMailHostValidatorTest {

    @Test
    void validate_allowsPrivateLiteralUsedByCorporateExchange() {
        assertDoesNotThrow(() ->
                SystemMailHostValidator.validate("10.20.30.40", Set.of("activepieces"), "System IMAP"));
    }

    @Test
    void validate_rejectsMetadataEvenIfAddedToAllowlist() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                SystemMailHostValidator.validate("169.254.169.254", Set.of("169.254.169.254"), "System IMAP"));
        assertTrue(ex.getMessage().contains("not allowed"));
    }

    @Test
    void validate_rejectsLoopbackAliasAfterDns() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                SystemMailHostValidator.validate("127.1", Set.of(), "System IMAP"));
        assertTrue(ex.getMessage().contains("blocked") || ex.getMessage().contains("not allowed"));
    }

    @Test
    void validate_rejectsLinkLocalEvenWhenNotMetadataLiteral() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                SystemMailHostValidator.validate("169.254.1.1", Set.of(), "System IMAP"));
        assertTrue(ex.getMessage().contains("blocked") || ex.getMessage().contains("not allowed"));
    }
}
