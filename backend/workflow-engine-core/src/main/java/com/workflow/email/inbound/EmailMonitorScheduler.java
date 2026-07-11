package com.workflow.email.inbound;

import com.platform.security.encryption.EncryptionService;
import com.workflow.email.extract.EmailMessage;
import com.workflow.email.inbound.entity.SysEmailConnection;
import com.workflow.email.inbound.entity.SysEmailMonitorRule;
import com.workflow.email.inbound.repository.SysEmailConnectionRepository;
import com.workflow.email.inbound.repository.SysEmailMonitorRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * Polls each enabled email monitor rule's mailbox for new messages (Power Automate-style
 * "when a new email arrives") and dispatches them to {@link EmailMonitorProcessor}.
 *
 * <p>The mailbox provider and credentials are entirely connection-determined: IMAP host comes
 * from the connection type preset, and the app password is decrypted from the synced connection.
 * Per-rule poll cadence is throttled by {@code pollIntervalSeconds}; idempotency is guaranteed by
 * the processor's ledger, and multi-replica polling is serialized by {@code @SchedulerLock}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailMonitorScheduler {

    private static final int MAX_PER_POLL = 20;

    private final SysEmailMonitorRuleRepository ruleRepository;
    private final SysEmailConnectionRepository connectionRepository;
    private final InboundMailClient imapClient;
    private final EmailMonitorProcessor processor;
    private final EncryptionService encryptionService;

    @Value("${workflow.email.monitor.enabled:true}")
    private boolean enabled;

    /**
     * 多副本下只让抢到锁的节点轮询邮箱。
     *
     * <p>重复处理本身已被 {@code we_email_processed_messages} 的 {@code UNIQUE(rule_uid, message_id)}
     * 挡住（见 {@link EmailMonitorProcessor}），所以这把锁不是为了正确性，而是为了避免
     * N 个副本对同一邮箱并发建 IMAP 连接（Gmail/Exchange 会限流甚至封连接），
     * 以及 {@code lastSyncedAt} 游标的后写覆盖。
     *
     * <p>{@code lockAtMostFor} 取 5 分钟：远大于一次轮询的正常耗时（20 封 × IMAP fetch），
     * 又能保证节点崩溃后锁最迟 5 分钟自动释放。{@code lockAtLeastFor} 取 10 秒，
     * 防止轮询很快返回时锁瞬间释放、另一副本立刻重复轮询。
     */
    @Scheduled(fixedDelayString = "${workflow.email.monitor.poll-delay-ms:30000}")
    @SchedulerLock(name = "EmailMonitor_poll", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void poll() {
        if (!enabled) {
            return;
        }
        List<SysEmailMonitorRule> rules;
        try {
            rules = ruleRepository.findByEnabledTrue();
        } catch (Exception e) {
            log.debug("Email monitor poll skipped (rules unavailable): {}", e.getMessage());
            return;
        }
        Instant now = Instant.now();
        for (SysEmailMonitorRule rule : rules) {
            if (isDue(rule, now)) {
                pollRule(rule);
            }
        }
    }

    private boolean isDue(SysEmailMonitorRule rule, Instant now) {
        if (rule.getLastSyncedAt() == null) {
            return true;
        }
        int interval = rule.getPollIntervalSeconds() != null ? rule.getPollIntervalSeconds() : 60;
        return now.isAfter(rule.getLastSyncedAt().plusSeconds(interval));
    }

    private void pollRule(SysEmailMonitorRule rule) {
        MailboxAccess access = resolveAccess(rule);
        if (access == null) {
            return;
        }
        try {
            FetchResult result = imapClient.fetchNew(
                    access, rule.getFolderLabel(), rule.getLastSyncCursor(), MAX_PER_POLL);
            for (EmailMessage email : result.messages()) {
                if (matchesFilters(rule, email)) {
                    processor.process(rule, email);
                }
            }
            persistCursor(rule, result.nextCursor());
        } catch (Exception e) {
            log.warn("Email monitor poll failed for rule {} ({}): {}",
                    rule.getId(), rule.getName(), e.getMessage());
        }
    }

    private MailboxAccess resolveAccess(SysEmailMonitorRule rule) {
        if (!StringUtils.hasText(rule.getConnectionUid())) {
            return null;
        }
        SysEmailConnection connection = connectionRepository.findById(rule.getConnectionUid()).orElse(null);
        if (connection == null || Boolean.FALSE.equals(connection.getEnabled())) {
            log.warn("[EMAIL-MONITOR] rule {} skipped: connection {} missing/disabled",
                    rule.getId(), rule.getConnectionUid());
            return null;
        }
        if (!isInbound(connection)) {
            log.warn("[EMAIL-MONITOR] rule {} skipped: connection {} direction={} is not inbound-capable",
                    rule.getId(), rule.getConnectionUid(), connection.getDirection());
            return null;
        }

        // Prefer explicitly configured IMAP endpoint; fall back to provider preset for known types.
        String host = connection.getImapHost();
        Integer port = connection.getImapPort();
        boolean ssl = connection.getImapUseSsl() == null || Boolean.TRUE.equals(connection.getImapUseSsl());
        if (!StringUtils.hasText(host) || port == null || port <= 0) {
            ImapProviderPreset preset = ImapProviderPreset.forType(connection.getConnectionType());
            if (preset == null) {
                log.warn("[EMAIL-MONITOR] rule {} skipped: connection {} type={} has no IMAP host/port configured and no preset available",
                        rule.getId(), rule.getConnectionUid(), connection.getConnectionType());
                return null;
            }
            host = preset.host();
            port = preset.port();
            if (connection.getImapUseSsl() == null) {
                ssl = preset.ssl();
            }
            log.info("[EMAIL-MONITOR] rule {} using IMAP preset for type {}: host={} port={} ssl={}",
                    rule.getId(), connection.getConnectionType(), host, port, ssl);
        } else {
            log.info("[EMAIL-MONITOR] rule {} using configured IMAP endpoint: host={} port={} ssl={}",
                    rule.getId(), host, port, ssl);
        }

        String username = StringUtils.hasText(connection.getMailboxAddress())
                ? connection.getMailboxAddress() : connection.getUsername();
        String password = decrypt(connection.getPasswordEncrypted());
        if (!StringUtils.hasText(username) || password == null) {
            log.warn("[EMAIL-MONITOR] rule {} skipped: connection {} missing IMAP credentials (username/password)",
                    rule.getId(), rule.getConnectionUid());
            return null;
        }
        return new MailboxAccess(host, port, ssl, username, password);
    }

    private boolean isInbound(SysEmailConnection connection) {
        String direction = connection.getDirection();
        return "INBOUND".equalsIgnoreCase(direction) || "BOTH".equalsIgnoreCase(direction);
    }

    private boolean matchesFilters(SysEmailMonitorRule rule, EmailMessage email) {
        if (StringUtils.hasText(rule.getFilterFrom())) {
            String from = email.from() != null ? email.from().toLowerCase() : "";
            if (!from.contains(rule.getFilterFrom().toLowerCase())) {
                return false;
            }
        }
        if (StringUtils.hasText(rule.getFilterSubject())) {
            String subject = email.subject() != null ? email.subject().toLowerCase() : "";
            return subject.contains(rule.getFilterSubject().toLowerCase());
        }
        return true;
    }

    private void persistCursor(SysEmailMonitorRule rule, String nextCursor) {
        rule.setLastSyncCursor(nextCursor);
        rule.setLastSyncedAt(Instant.now());
        ruleRepository.save(rule);
    }

    private String decrypt(String encrypted) {
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        try {
            return encryptionService.decrypt(encrypted);
        } catch (Exception e) {
            log.warn("Failed to decrypt mailbox password: {}", e.getMessage());
            return null;
        }
    }
}
