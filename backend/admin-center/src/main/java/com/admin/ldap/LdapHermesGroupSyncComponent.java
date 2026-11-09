package com.admin.ldap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
/**
 * Hermes AD Group → Admin Center 同步调度组件。
 *
 * <p>触发时机：
 * <ol>
 *   <li>应用启动后就绪（{@link ApplicationReadyEvent}）→ 异步执行一次组同步（对账）</li>
 *   <li>按 cron 表达式定时对账（默认每 2 小时，{@code ldap.sync-cron}）</li>
 * </ol>
 *
 * <p>每次都按虚拟组上的 AD 映射完整对账（见 {@link LdapSyncService}）。
 * 仅当 {@code ldap.enabled=true} 且 {@code ldap.sync-enabled=true} 时创建。</p>
 *
 * <p>两种触发与手动同步共用同一把 ShedLock 锁（{@link LdapSyncLock}，同步期间自动续期）：多副本时同一时刻只有一次
 * 同步在跑，锁被占（别的副本在跑定时 / 启动同步，或有人手动触发了同步）时，本副本这次启动 / 定时同步跳过并记 WARN，
 * 不排队补跑。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = {"enabled", "sync-enabled"}, havingValue = "true")
public class LdapHermesGroupSyncComponent {
    private final LdapSyncService ldapSyncService;
    private final LdapSyncLock syncLock;
    /** 应用就绪后异步触发一次 Hermes 组同步。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Hermes AD group sync enabled; scheduling startup sync");
        CompletableFuture.runAsync(this::runStartupSync);
    }
    /** 定时增量同步。cron 可配置（{@code ldap.sync-cron}）。 */
    @Scheduled(cron = "${ldap.sync-cron:0 0 */2 * * ?}")
    public void onSchedule() {
        runLocked("Scheduled");
    }
    void runStartupSync() {
        runLocked("Startup");
    }
    /** 拿与手动同步相同的锁跑一次组对账，拿不到（另一次同步正在跑）就跳过。 */
    private void runLocked(String trigger) {
        try {
            Optional<SimpleLock> lock = syncLock.tryAcquire(Duration.parse(LdapSyncLock.AT_LEAST_FOR));
            if (lock.isEmpty()) {
                log.warn("{} Hermes AD group sync skipped: another LDAP sync holds lock {}", trigger, LdapSyncLock.NAME);
                return;
            }
            try {
                log.info("{} Hermes AD group membership reconcile starting", trigger);
                ldapSyncService.runHermesAdGroupIncrementalSync();
            } finally {
                lock.get().unlock();
            }
        } catch (Exception e) {
            log.error("{} Hermes AD group sync error: {}", trigger, e.getMessage());
        }
    }
}