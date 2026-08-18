package com.workflow.email.inbound;

import com.platform.security.encryption.EncryptionService;
import com.workflow.client.AdminCenterSystemImapClient;
import com.workflow.email.extract.EmailMessage;
import com.workflow.email.inbound.entity.SysEmailConnection;
import com.workflow.email.inbound.entity.SysEmailMonitorRule;
import com.workflow.email.inbound.repository.SysEmailConnectionRepository;
import com.workflow.email.inbound.repository.SysEmailMonitorRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IMAP fetch failure must not look like a successful poll: cursor stays put and the processor
 * is not invoked.
 */
class EmailMonitorSchedulerTest {

    private SysEmailMonitorRuleRepository ruleRepository;
    private SysEmailConnectionRepository connectionRepository;
    private InboundMailClient imapClient;
    private EmailMonitorProcessor processor;
    private EmailMonitorScheduler scheduler;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(SysEmailMonitorRuleRepository.class);
        connectionRepository = mock(SysEmailConnectionRepository.class);
        imapClient = mock(InboundMailClient.class);
        processor = mock(EmailMonitorProcessor.class);
        EncryptionService encryptionService = mock(EncryptionService.class);
        AdminCenterSystemImapClient systemImapClient = mock(AdminCenterSystemImapClient.class);

        when(encryptionService.decrypt("enc")).thenReturn("secret");
        when(systemImapClient.fetchSystemImapEndpoint())
                .thenReturn(new AdminCenterSystemImapClient.SystemImapEndpoint("imap.example.test", 993, true));

        scheduler = new EmailMonitorScheduler(
                ruleRepository, connectionRepository, imapClient, processor,
                encryptionService, systemImapClient);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
    }

    @Test
    void imapFetchFailureDoesNotPersistCursorOrProcessMail() {
        SysEmailMonitorRule rule = enabledRule();
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        when(imapClient.fetchNew(any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("IMAP fetch failed for imap.example.test: connection refused"));

        String cursorBefore = rule.getLastSyncCursor();
        scheduler.poll();

        assertThat(rule.getLastSyncCursor()).isEqualTo(cursorBefore);
        verify(ruleRepository, never()).save(any());
        verify(processor, never()).process(any(), any());
    }

    @Test
    void secondTickSkipsWhileBackoffActive() {
        SysEmailMonitorRule rule = enabledRule();
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        when(imapClient.fetchNew(any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("IMAP fetch failed"));

        scheduler.poll();
        scheduler.poll();

        verify(imapClient).fetchNew(any(), any(), any(), anyInt());
    }

    @Test
    void processThrowDoesNotAdvanceCursor() {
        SysEmailMonitorRule rule = enabledRule();
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(rule));
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(inboundConnection()));
        EmailMessage email = new EmailMessage("m1", "s", "a@b.com", "body", null, Map.of());
        when(imapClient.fetchNew(any(), any(), any(), anyInt()))
                .thenReturn(new FetchResult(List.of(email), "11"));
        when(processor.process(any(), any())).thenThrow(new RuntimeException("startProcess failed"));

        scheduler.poll();

        assertThat(rule.getLastSyncCursor()).isEqualTo("10");
        verify(processor).process(any(), any());
        verify(ruleRepository).save(rule);
    }

    private static SysEmailMonitorRule enabledRule() {
        SysEmailMonitorRule rule = new SysEmailMonitorRule();
        rule.setId("rule-1");
        rule.setName("inbox");
        rule.setEnabled(true);
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
        connection.setPasswordEncrypted("enc");
        return connection;
    }
}
