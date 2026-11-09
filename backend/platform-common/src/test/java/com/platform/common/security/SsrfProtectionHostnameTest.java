package com.platform.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsrfProtectionHostnameTest {

    @Test
    void validateHostname_allowsPublicSmtpHost() {
        // An address literal: the check must not depend on this machine's DNS.
        assertDoesNotThrow(() -> SsrfProtection.validateHostname("8.8.8.8"));
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

    /** A host that does not resolve used to pass ("DNS failure, allowing"): nothing proves it is public. */
    @Test
    void validateHostname_refusesAHostThatDoesNotResolve() {
        SsrfProtection.SsrfException e = assertThrows(SsrfProtection.SsrfException.class,
                () -> SsrfProtection.validateHostname("no-such-host.invalid"));
        assertTrue(e.getMessage().contains("could not be resolved"));
    }

    @Test
    void validate_refusesAUrlWhoseHostDoesNotResolve() {
        assertThrows(SsrfProtection.SsrfException.class,
                () -> SsrfProtection.validate("https://no-such-host.invalid/hook"));
    }

    @Test
    void resolveValidated_returnsTheCheckedAddresses() {
        assertArrayEquals(new String[] {"8.8.8.8"}, hostAddresses(SsrfProtection.resolveValidated("8.8.8.8", Set.of())));
    }

    @Test
    void resolveValidated_refusesAnInternalAddressUnlessTheHostIsAllowed() {
        assertThrows(SsrfProtection.SsrfException.class, () -> SsrfProtection.resolveValidated("10.0.0.5", Set.of()));
        assertArrayEquals(new String[] {"10.0.0.5"},
                hostAddresses(SsrfProtection.resolveValidated("10.0.0.5", Set.of("10.0.0.5"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", "127.0.0.2", "169.254.1.1",
            "100.64.0.1", "0.1.2.3", "224.0.0.1", "::1", "fe80::1", "fc00::1", "fd12:3456::1", "::ffff:10.0.0.1",
            "::10.0.0.1", "::"})
    void internalAddressesAreRecognised(String literal) throws Exception {
        assertTrue(SsrfProtection.isInternalAddress(InetAddress.getByName(literal)), literal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "172.32.0.1", "100.128.0.1", "2001:4860:4860::8888"})
    void publicAddressesAreNot(String literal) throws Exception {
        assertFalse(SsrfProtection.isInternalAddress(InetAddress.getByName(literal)), literal);
    }

    private static String[] hostAddresses(InetAddress[] addresses) {
        String[] out = new String[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            out[i] = addresses[i].getHostAddress();
        }
        return out;
    }
}
