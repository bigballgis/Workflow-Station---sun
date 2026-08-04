package com.developer.component.impl;

import com.developer.client.AdminCenterSystemImapClient;
import com.developer.client.AdminCenterSystemSmtpClient;
import com.developer.entity.EmailConnection;
import com.developer.entity.FunctionUnit;
import com.developer.dto.EmailConnectionResponse;
import com.developer.enums.ConnectionType;
import com.developer.enums.EmailConnectionDirection;
import com.developer.dto.EmailConnectionRequest;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.EmailConnectionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.platform.common.i18n.I18nService;
import com.platform.security.encryption.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailConnectionComponentImplTest {

    @Mock
    private EmailConnectionRepository emailConnectionRepository;

    @Mock
    private FunctionUnitRepository functionUnitRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private I18nService i18nService;

    @Mock
    private AdminCenterSystemSmtpClient adminCenterSystemSmtpClient;

    @Mock
    private AdminCenterSystemImapClient adminCenterSystemImapClient;

    @InjectMocks
    private EmailConnectionComponentImpl emailConnectionComponent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailConnectionComponent, "ssrfAllowedHosts", List.of("localhost"));
    }

    @Test
    void testConnection_blankRecipient_throwsValidationException() {
        EmailConnection connection = sampleConnection(EmailConnectionDirection.OUTBOUND);

        when(emailConnectionRepository.findById(10L)).thenReturn(Optional.of(connection));
        when(i18nService.getMessage("email.connection.test_recipient_required"))
                .thenReturn("Test recipient is required");

        DeveloperBusinessException ex = assertThrows(
                DeveloperBusinessException.class,
                () -> emailConnectionComponent.testConnection(1L, 10L, "  "));

        assertEquals("VALIDATION_RECIPIENT_REQUIRED", ex.getErrorCode());
        assertEquals("Test recipient is required", ex.getMessage());
    }

    @Test
    void testConnection_outbound_usesSystemSmtp_andReturnsDetailOnFailure() {
        EmailConnection connection = sampleConnection(EmailConnectionDirection.OUTBOUND);

        when(emailConnectionRepository.findById(10L)).thenReturn(Optional.of(connection));
        when(adminCenterSystemSmtpClient.fetchSystemSmtpEndpoint())
                .thenReturn(new AdminCenterSystemSmtpClient.SystemSmtpEndpoint("localhost", 1, false));
        when(i18nService.getMessage(eq("email.connection.test_failed"), anyString()))
                .thenAnswer(invocation -> "Test failed: " + invocation.getArgument(1));

        Map<String, Object> result = emailConnectionComponent.testConnection(1L, 10L, "test@example.com");

        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("detail") instanceof String);
        assertFalse(((String) result.get("detail")).isBlank());
        assertTrue(((String) result.get("message")).startsWith("Test failed: "));
        assertTrue(result.containsKey("causeChain"));
    }

    @Test
    void testConnection_outbound_missingSystemSmtp_throws() {
        EmailConnection connection = sampleConnection(EmailConnectionDirection.OUTBOUND);

        when(emailConnectionRepository.findById(10L)).thenReturn(Optional.of(connection));
        when(adminCenterSystemSmtpClient.fetchSystemSmtpEndpoint())
                .thenThrow(new IllegalStateException("System SMTP host is not configured (smtp.host)"));
        when(i18nService.getMessage(eq("email.connection.system_smtp_required"), anyString()))
                .thenReturn("Global SMTP is not configured");

        DeveloperBusinessException ex = assertThrows(
                DeveloperBusinessException.class,
                () -> emailConnectionComponent.testConnection(1L, 10L, "test@example.com"));

        assertEquals("VALIDATION_SYSTEM_SMTP_REQUIRED", ex.getErrorCode());
    }

    @Test
    void testConnection_inboundOnly_throwsOutboundRequired() {
        EmailConnection connection = sampleConnection(EmailConnectionDirection.INBOUND);

        when(emailConnectionRepository.findById(10L)).thenReturn(Optional.of(connection));
        when(i18nService.getMessage("email.connection.outbound_required_for_test"))
                .thenReturn("Connection test requires outbound direction");

        DeveloperBusinessException ex = assertThrows(
                DeveloperBusinessException.class,
                () -> emailConnectionComponent.testConnection(1L, 10L, "test@example.com"));

        assertEquals("VALIDATION_OUTBOUND_REQUIRED_FOR_TEST", ex.getErrorCode());
    }

    @Test
    void create_outbound_doesNotRequireSystemImap() {
        FunctionUnit functionUnit = FunctionUnit.builder().id(1L).name("FU").build();
        EmailConnectionRequest request = new EmailConnectionRequest();
        request.setName("notify@example.com");
        request.setConnectionType(ConnectionType.SMTP);
        request.setUsername("svc");
        request.setPassword("pwd");
        request.setDirection(EmailConnectionDirection.OUTBOUND);
        request.setEnabled(true);

        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(emailConnectionRepository.existsByFunctionUnitIdAndNameAndDirection(
                eq(1L), eq("notify@example.com"), eq(EmailConnectionDirection.OUTBOUND))).thenReturn(false);
        when(adminCenterSystemSmtpClient.fetchSystemSmtpEndpoint())
                .thenReturn(new AdminCenterSystemSmtpClient.SystemSmtpEndpoint("smtp.local", 587, true));
        when(encryptionService.encrypt("pwd")).thenReturn("enc");
        when(emailConnectionRepository.save(any(EmailConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmailConnectionResponse response = emailConnectionComponent.create(1L, request);

        assertEquals("smtp.local", response.getHost());
        assertEquals(587, response.getPort());
        verify(adminCenterSystemImapClient, never()).fetchSystemImapEndpoint();
    }

    @Test
    void create_inbound_resolvesSystemImap() {
        FunctionUnit functionUnit = FunctionUnit.builder().id(1L).name("FU").build();
        EmailConnectionRequest request = new EmailConnectionRequest();
        request.setName("inbox@example.com");
        request.setConnectionType(ConnectionType.SMTP);
        request.setUsername("svc");
        request.setPassword("pwd");
        request.setDirection(EmailConnectionDirection.INBOUND);
        request.setEnabled(true);

        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(emailConnectionRepository.existsByFunctionUnitIdAndNameAndDirection(
                eq(1L), eq("inbox@example.com"), eq(EmailConnectionDirection.INBOUND))).thenReturn(false);
        when(adminCenterSystemImapClient.fetchSystemImapEndpoint())
                .thenReturn(new AdminCenterSystemImapClient.SystemImapEndpoint("imap.local", 993, true));
        when(encryptionService.encrypt("pwd")).thenReturn("enc");
        when(emailConnectionRepository.save(any(EmailConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmailConnectionResponse response = emailConnectionComponent.create(1L, request);

        assertEquals("imap.local", response.getImapHost());
        assertEquals(993, response.getImapPort());
        assertTrue(response.getImapUseSsl());
        verify(adminCenterSystemSmtpClient, never()).fetchSystemSmtpEndpoint();
    }

    @Test
    void create_inbound_allowedWhenOutboundExistsWithSameEmail() {
        FunctionUnit functionUnit = FunctionUnit.builder().id(1L).name("FU").build();
        EmailConnectionRequest request = new EmailConnectionRequest();
        request.setName("shared@example.com");
        request.setConnectionType(ConnectionType.SMTP);
        request.setUsername("svc");
        request.setPassword("pwd");
        request.setDirection(EmailConnectionDirection.INBOUND);
        request.setEnabled(true);

        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(emailConnectionRepository.existsByFunctionUnitIdAndNameAndDirection(
                eq(1L), eq("shared@example.com"), eq(EmailConnectionDirection.INBOUND))).thenReturn(false);
        when(adminCenterSystemImapClient.fetchSystemImapEndpoint())
                .thenReturn(new AdminCenterSystemImapClient.SystemImapEndpoint("imap.local", 993, true));
        when(encryptionService.encrypt("pwd")).thenReturn("enc");
        when(emailConnectionRepository.save(any(EmailConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmailConnectionResponse response = emailConnectionComponent.create(1L, request);

        assertEquals(EmailConnectionDirection.INBOUND, response.getDirection());
        assertEquals("shared@example.com", response.getName());
    }

    @Test
    void create_inbound_duplicateInbound_rejected() {
        FunctionUnit functionUnit = FunctionUnit.builder().id(1L).name("FU").build();
        EmailConnectionRequest request = new EmailConnectionRequest();
        request.setName("inbox@example.com");
        request.setConnectionType(ConnectionType.SMTP);
        request.setUsername("svc");
        request.setPassword("pwd");
        request.setDirection(EmailConnectionDirection.INBOUND);
        request.setEnabled(true);

        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(emailConnectionRepository.existsByFunctionUnitIdAndNameAndDirection(
                eq(1L), eq("inbox@example.com"), eq(EmailConnectionDirection.INBOUND))).thenReturn(true);
        when(i18nService.getMessage("email.connection.direction_label.INBOUND"))
                .thenReturn("Inbound (monitor)");
        when(i18nService.getMessage(
                eq("email.connection.name_conflict"), eq("Inbound (monitor)"), eq("inbox@example.com")))
                .thenReturn("Inbound conflict");

        DeveloperBusinessException ex = assertThrows(
                DeveloperBusinessException.class,
                () -> emailConnectionComponent.create(1L, request));

        assertEquals("CONFLICT_CONNECTION_NAME", ex.getErrorCode());
        verify(adminCenterSystemImapClient, never()).fetchSystemImapEndpoint();
    }

    @Test
    void create_bothDirection_rejected() {
        FunctionUnit functionUnit = FunctionUnit.builder().id(1L).name("FU").build();
        EmailConnectionRequest request = new EmailConnectionRequest();
        request.setName("notify@example.com");
        request.setConnectionType(ConnectionType.SMTP);
        request.setUsername("svc");
        request.setPassword("pwd");
        request.setDirection(EmailConnectionDirection.BOTH);
        request.setEnabled(true);

        when(functionUnitRepository.findById(1L)).thenReturn(Optional.of(functionUnit));
        when(i18nService.getMessage("email.connection.direction_both_removed"))
                .thenReturn("Both direction is no longer supported");

        DeveloperBusinessException ex = assertThrows(
                DeveloperBusinessException.class,
                () -> emailConnectionComponent.create(1L, request));

        assertEquals("VALIDATION_DIRECTION_BOTH_REMOVED", ex.getErrorCode());
        verify(adminCenterSystemSmtpClient, never()).fetchSystemSmtpEndpoint();
    }

    private static EmailConnection sampleConnection(EmailConnectionDirection direction) {
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(1L)
                .name("Test FU")
                .build();
        return EmailConnection.builder()
                .id(10L)
                .functionUnit(functionUnit)
                .connectionUid("conn-uid")
                .name("notify@example.com")
                .connectionType(ConnectionType.SMTP)
                .host("stale.example.com")
                .port(587)
                .fromEmail("notify@example.com")
                .useTls(true)
                .enabled(true)
                .direction(direction)
                .build();
    }
}
