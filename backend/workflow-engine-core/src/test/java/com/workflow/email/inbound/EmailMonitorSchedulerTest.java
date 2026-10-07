package com.workflow.email.inbound;

import com.workflow.client.AdminCenterClient;
import com.workflow.client.AdminCenterSystemImapClient;
import com.workflow.email.extract.EmailMessage;
import com.workflow.email.inbound.entity.SysEmailConnection;
import com.workflow.email.inbound.entity.SysEmailMonitorRule;
import com.workflow.email.inbound.repository.SysEmailConnectionRepository;
import com.workflow.email.inbound.repository.SysEmailMonitorRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailMonitorSchedulerTest {

    private SysEmailMonitorRuleRepository ruleRepository;
    private SysEmailConnectionRepository connectionRepository;
    private InboundMailClient imapClient;
    private EmailMonitorProcessor processor;
    private AdminCenterSystemImapClient systemImapClient;
    private AdminCenterClient adminCenterClient;
    private EmailMonitorScheduler scheduler;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(SysEmailMonitorRuleRepository.class);
        connectionRepository = mock(SysEmailConnectionRepository.class);
        imapClient = mock(InboundMailClient.class);
        processor = mock(EmailMonitorProcessor.class);
        systemImapClient = mock(AdminCenterSystemImapClient.class);
        adminCenterClient = mock(AdminCenterClient.class);

        when(systemImapClient.fetchSystemImapEndpoint())
                .thenReturn(new AdminCenterSystemImapClient.SystemImapEndpoint("imap.example.test", 993, true));
        when(adminCenterClient.getEmailConnectionCredentials("fu-1", "conn-1"))
                .thenReturn(Optional.of(Map.of("password", "secret")));
        when(ruleRepository.updatePollState(any(), any(), any(), any(), any())).thenReturn(1);

        scheduler = new EmailMonitorScheduler(
                ruleRepository, connectionRepository, imapClient, processor,
                adminCenterClient, systemImapClient);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
    }

    @Test
    void imapFetchFailureDoesNotPersistCursorOrProcessMail() {
        SysEmailMonitorRule rule = enabledRule();
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        when(imapClient.fetchNew(any(), any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("IMAP fetch failed for imap.example.test: connection refused"));

        String cursorBefore = rule.getLastSyncCursor();
        scheduler.poll();

        assertThat(rule.getLastSyncCursor()).isEqualTo(cursorBefore);
        verify(ruleRepository, never()).updatePollState(any(), any(), any(), any(), any());
        verify(processor, never()).process(any(), any());
    }

    @Test
    void vaultPasswordFailureDoesNotTreatAsMissingCredentialsOrFetchImap() {
        SysEmailMonitorRule rule = enabledRule();
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        when(adminCenterClient.getEmailConnectionCredentials("fu-1", "conn-1"))
                .thenThrow(new IllegalStateException(
                        "Vault secret not found for environment variable email.qq.inbound.password"));

        scheduler.poll();

        verify(imapClient, never()).fetchNew(any(), any(), any(), any(), anyInt());
        verify(processor, never()).process(any(), any());
    }

    @Test
    void secondTickSkipsWhileBackoffActive() {
        SysEmailMonitorRule rule = enabledRule();
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        when(imapClient.fetchNew(any(), any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("IMAP fetch failed"));

        scheduler.poll();
        scheduler.poll();

        verify(imapClient).fetchNew(any(), any(), any(), any(), anyInt());
    }

    @Test
    void redeployToAnotherConnectionPollsNewMailboxDespiteOldBackoff() {
        SysEmailMonitorRule rule = enabledRule();
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        when(imapClient.fetchNew(any(), any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("IMAP fetch failed"));
        scheduler.poll();

        SysEmailConnection newConnection = inboundConnection();
        newConnection.setId("conn-2");
        when(connectionRepository.findById("conn-2")).thenReturn(Optional.of(newConnection));
        when(adminCenterClient.getEmailConnectionCredentials("fu-1", "conn-2"))
                .thenReturn(Optional.of(Map.of("password", "secret")));
        rule.setConnectionUid("conn-2");
        rule.setLastSyncCursor(null);
        scheduler.poll();

        verify(imapClient, times(2)).fetchNew(any(), any(), any(), any(), anyInt());
    }

    @Test
    void firstPollAfterDeployFetchesMailReceivedSinceDeploy() {
        SysEmailMonitorRule rule = enabledRule();
        rule.setLastSyncCursor(null);
        Instant deployedAt = Instant.now().minus(Duration.ofMinutes(10));
        rule.setSyncedAt(deployedAt);
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        when(imapClient.fetchNew(any(), any(), any(), any(), anyInt()))
                .thenReturn(new FetchResult(List.of(), "1"));

        scheduler.poll();

        verify(imapClient).fetchNew(any(), eq("INBOX"), isNull(), eq(deployedAt), anyInt());
    }

    @Test
    void ruleWithCursorDoesNotCatchUp() {
        SysEmailMonitorRule rule = enabledRule();
        rule.setSyncedAt(Instant.now().minus(Duration.ofMinutes(10)));

        assertThat(EmailMonitorScheduler.watchFrom(rule, Instant.now())).isNull();
    }

    @Test
    void catchUpAfterLongOutageIsBoundedToMaxWindow() {
        SysEmailMonitorRule rule = enabledRule();
        rule.setLastSyncCursor(null);
        Instant now = Instant.parse("2026-10-07T09:00:00Z");
        rule.setSyncedAt(now.minus(Duration.ofDays(5)));

        assertThat(EmailMonitorScheduler.watchFrom(rule, now))
                .isEqualTo(now.minus(EmailMonitorScheduler.MAX_CATCH_UP));
    }

    /**
     * Admin rebinds the rule while an IMAP fetch is in flight. Writing back the snapshot read
     * before the fetch would put the old connection and its cursor back into the row.
     */
    @Test
    void redeployDuringPollDoesNotRestoreOldConnectionOrCursor() {
        SysEmailMonitorRule rule = enabledRule();
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        when(imapClient.fetchNew(any(), any(), any(), any(), anyInt()))
                .thenReturn(new FetchResult(List.of(), "11"));
        when(ruleRepository.updatePollState(any(), any(), any(), any(), any())).thenReturn(0);

        scheduler.poll();

        verify(ruleRepository, never()).save(any());
        verifyPollStateWritten("11");
        assertThat(rule.getLastSyncCursor()).isEqualTo("10");
    }

    @Test
    void noEnabledRulesDoesNotFetch() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of());

        scheduler.poll();
        scheduler.poll();

        verify(imapClient, never()).fetchNew(any(), any(), any(), any(), anyInt());
        verify(processor, never()).process(any(), any());
    }

    @Test
    void filterMismatchDoesNotProcessAndAdvancesCursor() {
        SysEmailMonitorRule rule = enabledRule();
        rule.setFilterFrom("alerts@example.test");
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        EmailMessage email = new EmailMessage("m1", "s", "other@example.test", "body", null, Map.of());
        when(imapClient.fetchNew(any(), any(), any(), any(), anyInt()))
                .thenReturn(new FetchResult(List.of(email), "11"));

        scheduler.poll();

        assertThat(rule.getLastSyncCursor()).isEqualTo("11");
        verify(processor, never()).process(any(), any());
        verifyPollStateWritten("11");
    }

    @Test
    void processThrowDoesNotAdvanceCursor() {
        SysEmailMonitorRule rule = enabledRule();
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        EmailMessage email = new EmailMessage("m1", "s", "a@b.com", "body", null, Map.of());
        when(imapClient.fetchNew(any(), any(), any(), any(), anyInt()))
                .thenReturn(new FetchResult(List.of(email), "11"));
        when(processor.process(any(), any())).thenThrow(new RuntimeException("startProcess failed"));

        scheduler.poll();

        assertThat(rule.getLastSyncCursor()).isEqualTo("10");
        verify(processor).process(any(), any());
        verifyPollStateWritten("10");
    }

    /**
     * A transient delivery failure must keep the cursor so the same mail is re-fetched; recording
     * it in the ledger instead would skip that email forever and the case would never exist.
     */
    @Test
    void retryableDeliveryFailureDoesNotAdvanceCursor() {
        SysEmailMonitorRule rule = enabledRule();
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        EmailMessage email = new EmailMessage("m1", "s", "a@b.com", "body", null, Map.of());
        when(imapClient.fetchNew(any(), any(), any(), any(), anyInt()))
                .thenReturn(new FetchResult(List.of(email), "11"));
        when(processor.process(any(), any()))
                .thenThrow(new EmailMonitorRetryableException("portal down", 1, 3));

        scheduler.poll();

        assertThat(rule.getLastSyncCursor()).isEqualTo("10");
        verifyPollStateWritten("10");
    }

    @Test
    void reviewStatusAdvancesCursorWithoutStartProcessThrow() {
        SysEmailMonitorRule rule = enabledRule();
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        EmailMessage email = new EmailMessage("m1", "s", "a@b.com", "body", null, Map.of());
        when(imapClient.fetchNew(any(), any(), any(), any(), anyInt()))
                .thenReturn(new FetchResult(List.of(email), "11"));
        when(processor.process(any(), any())).thenReturn("REVIEW");

        scheduler.poll();

        assertThat(rule.getLastSyncCursor()).isEqualTo("11");
        verify(processor).process(any(), any());
        verifyPollStateWritten("11");
    }

    @Test
    void usesLiveAdminImapHostEvenWhenConnectionStoresDifferentHost() {
        SysEmailMonitorRule rule = enabledRule();
        SysEmailConnection connection = inboundConnection();
        connection.setImapHost("imap.qq.com");
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(connection));
        when(systemImapClient.fetchSystemImapEndpoint())
                .thenReturn(new AdminCenterSystemImapClient.SystemImapEndpoint("10.20.30.40", 993, true));
        when(imapClient.fetchNew(any(), any(), any(), any(), anyInt()))
                .thenReturn(new FetchResult(List.of(), "1"));

        scheduler.poll();

        ArgumentCaptor<MailboxAccess> captor = ArgumentCaptor.forClass(MailboxAccess.class);
        verify(imapClient).fetchNew(captor.capture(), any(), any(), any(), anyInt());
        assertThat(captor.getValue().host()).isEqualTo("10.20.30.40");
        assertThat(captor.getValue().port()).isEqualTo(993);
        assertThat(captor.getValue().ssl()).isTrue();
        assertThat(captor.getValue().username()).isEqualTo("monitor@example.test");
    }

    @Test
    void pollUsesServiceAccountLoginAndBoundMailbox() {
        SysEmailMonitorRule rule = enabledRule();
        SysEmailConnection connection = inboundConnection();
        connection.setUsername("ADRES-SVC-HMS-NP");
        connection.setMailboxAddress("hk.hermes.mailin@example.test");
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(connection));
        when(imapClient.fetchNew(any(), any(), any(), any(), anyInt()))
                .thenReturn(new FetchResult(List.of(), "1"));

        scheduler.poll();

        ArgumentCaptor<MailboxAccess> captor = ArgumentCaptor.forClass(MailboxAccess.class);
        verify(imapClient).fetchNew(captor.capture(), any(), any(), any(), anyInt());
        assertThat(captor.getValue().username()).isEqualTo("ADRES-SVC-HMS-NP");
        assertThat(captor.getValue().mailboxAddress()).isEqualTo("hk.hermes.mailin@example.test");
    }

    @Test
    void resolveIdentities_loginIsUsername_mailboxFallsBackToFromEmail() {
        SysEmailConnection connection = new SysEmailConnection();
        connection.setUsername("ADRES-SVC-HMS-NP");
        connection.setFromEmail("hk.hermes.mailin@example.test");

        EmailMonitorScheduler.ImapIdentities ids = EmailMonitorScheduler.resolveIdentities(connection);

        assertThat(ids.login()).isEqualTo("ADRES-SVC-HMS-NP");
        assertThat(ids.mailbox()).isEqualTo("hk.hermes.mailin@example.test");
    }

    private void verifyPollStateWritten(String cursor) {
        verify(ruleRepository).updatePollState(eq("rule-1"), eq("conn-1"), eq("INBOX"), eq(cursor), any());
    }

    private static SysEmailMonitorRule enabledRule() {
        SysEmailMonitorRule rule = new SysEmailMonitorRule();
        rule.setId("rule-1");
        rule.setName("inbox");
        rule.setEnabled(true);
        rule.setFunctionUnitId("fu-1");
        rule.setConnectionUid("conn-1");
        rule.setFolderLabel("INBOX");
        rule.setPollIntervalSeconds(60);
        rule.setLastSyncCursor("10");
        return rule;
    }

    private static SysEmailConnection inboundConnection() {
        SysEmailConnection connection = new SysEmailConnection();
        connection.setId("conn-1");
        connection.setEnabled(true);
        connection.setDirection("INBOUND");
        connection.setMailboxAddress("monitor@example.test");
        connection.setPasswordEnvKey("email.qq.inbound.password");
        return connection;
    }
}
