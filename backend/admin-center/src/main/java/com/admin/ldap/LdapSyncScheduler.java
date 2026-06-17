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
 * LDAP 用户同步调度：应用启动后触发一次 + 按 cron 定时触发（默认每 2 小时）。
 *
 * <p>仅当 {@code ldap.enabled=true} 且 {@code ldap.sync-enabled=true} 时创建。
 * 启动同步异步执行，避免阻塞应用就绪；定时同步运行增量（无基线时自动回退全量）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = {"enabled", "sync-enabled"}, havingValue = "true")
public class LdapSyncScheduler {

    private final LdapUserSyncService ldapUserSyncService;

    /** 应用就绪后异步触发一次增量同步（建立/刷新基线）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("LDAP sync enabled; scheduling initial incremental sync on startup");
        CompletableFuture.runAsync(this::runIncrementalQuietly);
    }

    /** 定时增量同步。cron 可配置（{@code ldap.sync-cron}）。 */
    @Scheduled(cron = "${ldap.sync-cron:0 0 */2 * * ?}")
    public void onSchedule() {
        runIncrementalQuietly();
    }

    private void runIncrementalQuietly() {
        try {
            ldapUserSyncService.runIncrementalSync();
        } catch (Exception e) {
            // 同步异常已在 service 内写审计并记日志，这里兜底防止线程异常逃逸
            log.error("Scheduled LDAP incremental sync error: {}", e.getMessage());
        }
    }
}
