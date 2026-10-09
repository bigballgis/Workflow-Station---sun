package com.workflow.email.inbound;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.platform.common.mail.ImapTransportProperties;
import com.workflow.client.AdminCenterClient;
import com.workflow.client.AdminCenterSystemImapClient;
import com.workflow.email.extract.EmailMessage;
import com.workflow.email.inbound.entity.ProcessedEmailMessage;
import com.workflow.email.inbound.entity.SysEmailConnection;
import com.workflow.email.inbound.entity.SysEmailMonitorRule;
import com.workflow.email.inbound.repository.SysEmailConnectionRepository;
import com.workflow.email.inbound.repository.SysEmailMonitorRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scheduler + IMAP client against a live IMAP server: a monitor whose mailbox kept failing is
 * redeployed onto another connection, and mail sent right after the deploy must start a process.
 */
class EmailMonitorRebindGreenMailTest {

    private static final ServerSetup IMAPS = new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_IMAPS);
    private static final ServerSetup SMTP = new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_SMTP);

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(new ServerSetup[] {SMTP, IMAPS});

    private SysEmailMonitorRuleRepository ruleRepository;
    private SysEmailConnectionRepository connectionRepository;
    private EmailMonitorProcessor processor;
    private AdminCenterClient adminCenterClient;
    private EmailMonitorScheduler scheduler;
    private SysEmailMonitorRule rule;

    @BeforeEach
    void setUp() {
        greenMail.setUser("old@corp.test", "old-secret");
        greenMail.setUser("new@corp.test", "new-secret");
        int port = greenMail.getImaps().getPort();

        ruleRepository = mock(SysEmailMonitorRuleRepository.class);
        connectionRepository = mock(SysEmailConnectionRepository.class);
        processor = mock(EmailMonitorProcessor.class);
        adminCenterClient = mock(AdminCenterClient.class);
        AdminCenterSystemImapClient systemImap = mock(AdminCenterSystemImapClient.class);
        when(systemImap.fetchSystemImapEndpoint())
                .thenReturn(new AdminCenterSystemImapClient.SystemImapEndpoint("localhost", port, true));
        when(connectionRepository.findById("conn-old")).thenReturn(Optional.of(connection("conn-old", "old@corp.test")));
        when(connectionRepository.findById("conn-new")).thenReturn(Optional.of(connection("conn-new", "new@corp.test")));
        // The old mailbox's password was rotated, which is why the user switched connections.
        when(adminCenterClient.getEmailConnectionCredentials("fu-1", "conn-old"))
                .thenReturn(Optional.of(Map.of("password", "rotated-away")));
        when(adminCenterClient.getEmailConnectionCredentials("fu-1", "conn-new"))
                .thenReturn(Optional.of(Map.of("password", "new-secret")));
        when(processor.process(any(), any())).thenReturn(ProcessedEmailMessage.STATUS_STARTED);

        rule = new SysEmailMonitorRule();
        rule.setId("rule-1");
        rule.setName("Start from inbound email");
        rule.setEnabled(true);
        rule.setFunctionUnitId("fu-1");
        rule.setConnectionUid("conn-old");
        rule.setFolderLabel("INBOX");
        rule.setPollIntervalSeconds(60);
        rule.setSyncedAt(Instant.now().minusSeconds(3600));
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(ruleRepository.updatePollState(any(), any(), any(), any(), any())).thenReturn(1);

        scheduler = new EmailMonitorScheduler(ruleRepository, connectionRepository,
                trustingLocalCert(port), processor, adminCenterClient, systemImap);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
    }

    @Test
    void mailSentRightAfterRedeployToNewConnectionStartsProcess() throws Exception {
        scheduler.poll();
        verify(processor, times(0)).process(any(), any());
        verify(ruleRepository, times(0)).updatePollState(any(), any(), any(), any(), any());

        send("new@corp.test", "Before deploy");
        Thread.sleep(1100);
        redeployOnto("conn-new", Instant.now());
        Thread.sleep(1100);
        send("new@corp.test", "Sent right after deploy");

        scheduler.poll();

        assertThat(processedSubjects()).containsExactly("Sent right after deploy");
        verify(ruleRepository, atLeastOnce())
                .updatePollState(eq("rule-1"), eq("conn-new"), eq("INBOX"), any(), any());
        assertThat(rule.getLastSyncCursor()).isNotBlank();

        send("new@corp.test", "Next mail");
        rule.setLastSyncedAt(rule.getLastSyncedAt().minusSeconds(61));
        scheduler.poll();

        assertThat(processedSubjects()).containsExactly("Sent right after deploy", "Next mail");
    }

    /** What Admin's sync leaves in the row when the connection changed: new target, no cursor. */
    private void redeployOnto(String connectionUid, Instant deployedAt) {
        rule.setConnectionUid(connectionUid);
        rule.setLastSyncCursor(null);
        rule.setLastSyncedAt(null);
        rule.setSyncedAt(deployedAt);
    }

    private List<String> processedSubjects() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(processor, atLeastOnce()).process(any(), captor.capture());
        return captor.getAllValues().stream().map(EmailMessage::subject).toList();
    }

    private static void send(String to, String subject) {
        GreenMailUtil.sendTextEmail(to, "sender@corp.test", subject, "please create case",
                greenMail.getSmtp().getServerSetup());
    }

    /** Production client; only the self-signed test certificate needs trusting. */
    private static InboundMailClient trustingLocalCert(int port) {
        ImapInboundMailClient client = new ImapInboundMailClient();
        return (access, folder, cursor, watchFrom, max) -> {
            Properties props = ImapTransportProperties.apply("localhost", port, true, "imaps");
            props.put("mail.imaps.ssl.checkserveridentity", "false");
            return client.fetchNew(access,
                    new ImapInboundMailClient.FetchRequest(folder, cursor, watchFrom, max), props);
        };
    }

    private static SysEmailConnection connection(String id, String mailbox) {
        SysEmailConnection connection = new SysEmailConnection();
        connection.setId(id);
        connection.setEnabled(true);
        connection.setDirection("INBOUND");
        connection.setUsername(mailbox);
        connection.setMailboxAddress(mailbox);
        connection.setPasswordEnvKey("email." + id + ".password");
        return connection;
    }
}
