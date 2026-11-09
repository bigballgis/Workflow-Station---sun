package com.workflow.service;

import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.i18n.I18nService;
import com.workflow.client.AdminCenterClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A template field left empty is published as null: it is empty text, never the word "null". */
class ConfiguredEmailTemplateContentTest {
    private static final Map<String, String> TEMPLATED_EMAIL = Map.of(
            "connectionId", "mail", "emailTo", "fixture@example.test", "emailTemplateId", "501");
    private static final Map<String, Object> VARIABLES = Map.of("functionUnitId", "rel-1", "amount", "42");

    private final AdminCenterClient admin = mock(AdminCenterClient.class);
    private final CatalogSnapshotReader snapshots = mock(CatalogSnapshotReader.class);
    private final EmailSenderService emailSenderService = mock(EmailSenderService.class);
    private final TextEnvironmentInterpolator interpolator = mock(TextEnvironmentInterpolator.class);
    /** Every message is its key, so assertions name the rule that refused. */
    private final I18nService i18n = mock(I18nService.class, call -> call.getArgument(0));
    private ConfiguredEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new ConfiguredEmailSender(admin, mock(EmailAttachmentResolver.class), emailSenderService,
                interpolator, i18n);
        ReflectionTestUtils.setField(sender, "pinnedReleaseLookup",
                new com.workflow.component.PinnedReleaseLookup(mock(JdbcTemplate.class)));
        ReflectionTestUtils.setField(sender, "catalogSnapshots", snapshots);
        when(interpolator.apply(anyString(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        when(admin.getEmailConnectionCredentials("rel-1", "mail")).thenReturn(Optional.of(Map.of("host", "smtp")));
    }

    @Test
    void templateSubjectAndBodyAreSentResolved() throws Exception {
        template("Amount ${amount}", "<p>${amount}</p>");

        sender.send("activity=Send_1", TEMPLATED_EMAIL, VARIABLES, "pi-1");

        EmailSendOptions sent = sentOptions();
        assertThat(sent.subject()).isEqualTo("Amount 42");
        assertThat(sent.body()).isEqualTo("<p>42</p>");
    }

    @Test
    void templateWithoutBodySendsAnEmptyBodyNotTheWordNull() throws Exception {
        template("Amount ${amount}", null);

        sender.send("activity=Send_1", TEMPLATED_EMAIL, VARIABLES, "pi-1");

        assertThat(sentOptions().body()).isEmpty();
    }

    @Test
    void templateWithoutSubjectIsRefusedAndNothingIsSent() throws Exception {
        template(null, "<p>${amount}</p>");

        assertThatThrownBy(() -> sender.send("activity=Send_1", TEMPLATED_EMAIL, VARIABLES, "pi-1"))
                .isInstanceOf(EmailDeliveryException.class)
                .satisfies(error -> {
                    EmailDeliveryException failure = (EmailDeliveryException) error;
                    assertThat(failure.getCode()).isEqualTo(EmailDeliveryException.CONFIG_INVALID);
                    assertThat(failure.isTransientFailure()).isFalse();
                    assertThat(failure.getMessage()).isEqualTo("email.send_task.missing_subject");
                });
        verify(emailSenderService, never()).send(any(), any());
    }

    private void template(String subject, String bodyHtml) {
        Map<String, Object> template = new HashMap<>();
        template.put("templateId", "501");
        template.put("subject", subject);
        template.put("bodyHtml", bodyHtml);
        when(snapshots.contents("rel-1", "EMAIL_TEMPLATE")).thenReturn(List.of(template));
    }

    private EmailSendOptions sentOptions() throws Exception {
        ArgumentCaptor<EmailSendOptions> options = ArgumentCaptor.forClass(EmailSendOptions.class);
        verify(emailSenderService).send(any(), options.capture());
        return options.getValue();
    }
}
