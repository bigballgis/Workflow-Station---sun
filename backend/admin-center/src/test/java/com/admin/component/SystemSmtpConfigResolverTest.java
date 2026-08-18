package com.admin.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSmtpConfigResolverTest {

    @Mock
    private ConfigManagerComponent configManager;

    @InjectMocks
    private SystemSmtpConfigResolver resolver;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resolver, "ssrfAllowedHosts", List.of("localhost", "smtp.example.com"));
    }

    @Test
    void isOutboundCapable_defaultsAndDirections() {
        assertTrue(SystemSmtpConfigResolver.isOutboundCapable(null));
        assertTrue(SystemSmtpConfigResolver.isOutboundCapable(""));
        assertTrue(SystemSmtpConfigResolver.isOutboundCapable("OUTBOUND"));
        assertTrue(SystemSmtpConfigResolver.isOutboundCapable("both"));
        assertFalse(SystemSmtpConfigResolver.isOutboundCapable("INBOUND"));
    }

    @Test
    void requireSystemSmtpEndpoint_returnsConfiguredValues() {
        when(configManager.getConfigValue(SystemSmtpConfigResolver.KEY_HOST)).thenReturn("smtp.example.com");
        when(configManager.getConfigValue(SystemSmtpConfigResolver.KEY_PORT)).thenReturn("587");
        when(configManager.getConfigValue(SystemSmtpConfigResolver.KEY_USE_TLS)).thenReturn("true");

        SystemSmtpConfigResolver.SystemSmtpEndpoint endpoint = resolver.requireSystemSmtpEndpoint();

        assertEquals("smtp.example.com", endpoint.host());
        assertEquals(587, endpoint.port());
        assertTrue(endpoint.useTls());
    }

    @Test
    void requireSystemSmtpEndpoint_missingHost_throws() {
        when(configManager.getConfigValue(SystemSmtpConfigResolver.KEY_HOST)).thenReturn("  ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::requireSystemSmtpEndpoint);
        assertTrue(ex.getMessage().contains("smtp.host"));
    }

    @Test
    void requireSystemSmtpEndpoint_invalidPort_throws() {
        when(configManager.getConfigValue(SystemSmtpConfigResolver.KEY_HOST)).thenReturn("smtp.example.com");
        when(configManager.getConfigValue(SystemSmtpConfigResolver.KEY_PORT)).thenReturn("abc");

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::requireSystemSmtpEndpoint);
        assertTrue(ex.getMessage().contains("smtp.port"));
    }

    @Test
    void requireSystemSmtpEndpoint_allowsConfiguredIntranetHost() {
        ReflectionTestUtils.setField(resolver, "ssrfAllowedHosts", List.of("activepieces"));
        when(configManager.getConfigValue(SystemSmtpConfigResolver.KEY_HOST)).thenReturn("10.20.30.40");
        when(configManager.getConfigValue(SystemSmtpConfigResolver.KEY_PORT)).thenReturn("587");
        when(configManager.getConfigValue(SystemSmtpConfigResolver.KEY_USE_TLS)).thenReturn("true");

        SystemSmtpConfigResolver.SystemSmtpEndpoint endpoint = resolver.requireSystemSmtpEndpoint();

        assertEquals("10.20.30.40", endpoint.host());
        assertEquals(587, endpoint.port());
        assertTrue(endpoint.useTls());
    }
}
