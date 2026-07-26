package com.developer.component.impl;

import com.developer.entity.EmailConnection;
import com.developer.entity.FunctionUnit;
import com.developer.enums.ConnectionType;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private EmailConnectionComponentImpl emailConnectionComponent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailConnectionComponent, "ssrfAllowedHosts", List.of("localhost"));
    }

    @Test
    void testConnection_blankRecipient_throwsValidationException() {
        EmailConnection connection = sampleConnection();

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
    void testConnection_smtpFailure_returnsDetailField() {
        EmailConnection connection = sampleConnection();

        when(emailConnectionRepository.findById(10L)).thenReturn(Optional.of(connection));
        when(i18nService.getMessage(eq("email.connection.test_failed"), anyString()))
                .thenAnswer(invocation -> "Test failed: " + invocation.getArgument(1));

        Map<String, Object> result = emailConnectionComponent.testConnection(1L, 10L, "test@example.com");

        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("detail") instanceof String);
        assertFalse(((String) result.get("detail")).isBlank());
        assertTrue(((String) result.get("message")).startsWith("Test failed: "));
        assertTrue(result.containsKey("causeChain"));
    }

    private static EmailConnection sampleConnection() {
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
                .host("localhost")
                .port(1)
                .fromEmail("notify@example.com")
                .useTls(false)
                .enabled(true)
                .build();
    }
}
