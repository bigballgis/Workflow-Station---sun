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
class SystemImapConfigResolverTest {

    @Mock
    private ConfigManagerComponent configManager;

    @InjectMocks
    private SystemImapConfigResolver resolver;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resolver, "ssrfAllowedHosts", List.of("localhost", "imap.example.com"));
    }

    @Test
    void isInboundCapable_directions() {
        assertFalse(SystemImapConfigResolver.isInboundCapable(null));
        assertFalse(SystemImapConfigResolver.isInboundCapable(""));
        assertFalse(SystemImapConfigResolver.isInboundCapable("OUTBOUND"));
        assertTrue(SystemImapConfigResolver.isInboundCapable("INBOUND"));
        assertTrue(SystemImapConfigResolver.isInboundCapable("both"));
    }

    @Test
    void requireSystemImapEndpoint_returnsConfiguredValues() {
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_HOST)).thenReturn("imap.example.com");
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_PORT)).thenReturn("993");
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_USE_SSL)).thenReturn("true");

        SystemImapConfigResolver.SystemImapEndpoint endpoint = resolver.requireSystemImapEndpoint();

        assertEquals("imap.example.com", endpoint.host());
        assertEquals(993, endpoint.port());
        assertTrue(endpoint.useSsl());
    }

    @Test
    void requireSystemImapEndpoint_missingHost_throws() {
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_HOST)).thenReturn("  ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::requireSystemImapEndpoint);
        assertTrue(ex.getMessage().contains("imap.host"));
    }

    @Test
    void requireSystemImapEndpoint_allowsConfiguredIntranetHost() {
        ReflectionTestUtils.setField(resolver, "ssrfAllowedHosts", List.of("activepieces"));
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_HOST)).thenReturn("imap.corp.internal");
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_PORT)).thenReturn("993");
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_USE_SSL)).thenReturn("true");

        SystemImapConfigResolver.SystemImapEndpoint endpoint = resolver.requireSystemImapEndpoint();

        assertEquals("imap.corp.internal", endpoint.host());
        assertEquals(993, endpoint.port());
    }

    @Test
    void requireSystemImapEndpoint_rejectsMetadataHost() {
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_HOST)).thenReturn("169.254.169.254");

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::requireSystemImapEndpoint);
        assertTrue(ex.getMessage().contains("not allowed"));
    }

    @Test
    void requireSystemImapEndpoint_rejectsLoopbackHost() {
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_HOST)).thenReturn("127.0.0.1");

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::requireSystemImapEndpoint);
        assertTrue(ex.getMessage().contains("not allowed"));
    }

    @Test
    void requireSystemImapEndpoint_allowsConfiguredPrivateLiteral() {
        ReflectionTestUtils.setField(resolver, "ssrfAllowedHosts", List.of("activepieces"));
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_HOST)).thenReturn("10.20.30.40");
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_PORT)).thenReturn("143");
        when(configManager.getConfigValue(SystemImapConfigResolver.KEY_USE_SSL)).thenReturn("false");

        SystemImapConfigResolver.SystemImapEndpoint endpoint = resolver.requireSystemImapEndpoint();

        assertEquals("10.20.30.40", endpoint.host());
        assertEquals(143, endpoint.port());
        assertFalse(endpoint.useSsl());
    }
}
