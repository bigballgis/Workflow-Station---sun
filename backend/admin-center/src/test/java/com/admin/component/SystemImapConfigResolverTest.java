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
}
