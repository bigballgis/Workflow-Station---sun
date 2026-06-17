package com.admin.ldap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Hermes AD Group → Admin Center 同步调度组件。
 *
 * <p>触发时机：
 * <ol>
 *   <li>应用启动后就绪（{@link ApplicationReadyEvent}）→ 异步执行一次增量同步</li>
 *   <li>按 cron 表达式定时触发增量同步（默认每 2 小时，{@code ldap.sync-cron}）</li>
 * </ol>
 *
 * <p>增量同步无基线时自动降级为全量（见 {@link LdapSyncService#runHermesAdGroupIncrementalSync}）。
 * 仅当 {@code ldap.enabled=true} 且 {@code ldap.sync-enabled=true} 时创建。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = {"enabled", "sync-enabled"}, havingValue = "true")
public class LdapHermesGroupSyncComponent {

    private final LdapSyncService ldapSyncService;

    /** 应用就绪后异步触发一次 Hermes 组增量同步。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Hermes AD group sync enabled; scheduling initial incremental sync on startup");
        CompletableFuture.runAsync(this::runIncrementalQuietly);
    }

    /** 定时增量同步。cron 可配置（{@code ldap.sync-cron}）。 */
    @Scheduled(cron = "${ldap.sync-cron:0 0 */2 * * ?}")
    public void onSchedule() {
        runIncrementalQuietly();
    }

    private void runIncrementalQuietly() {
        try {
            ldapSyncService.runHermesAdGroupIncrementalSync();
        } catch (Exception e) {
            log.error("Scheduled Hermes AD group incremental sync error: {}", e.getMessage());
        }
    }
}
