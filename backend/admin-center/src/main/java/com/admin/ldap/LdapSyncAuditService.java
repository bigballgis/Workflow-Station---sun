package com.admin.ldap;

import com.admin.dto.response.PageResult;
import com.admin.exception.AdminConflictException;
import com.admin.exception.AdminServiceUnavailableException;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * LDAP 同步的查询与异步触发服务（控制器与同步实现/仓库之间的编排层）。
 *
 * <p>手动触发先在请求线程里拿 {@link LdapSyncLock}（与启动、定时同步同一把锁），拿到后交给
 * {@link LdapManualSyncRunner} 异步执行，避免长同步阻塞 HTTP 请求；调用方通过审计接口轮询进度。
 * 另一次同步正持锁时直接报 409（{@code LDAP_SYNC_IN_PROGRESS}），不排队、不静默跳过；锁库连不上时报 503。
 * 仅 {@code ldap.enabled=true} 时创建。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = {"enabled", "sync-enabled"}, havingValue = "true")
public class LdapSyncAuditService {

    private final LdapUserSyncService ldapUserSyncService;
    private final LdapSyncService ldapSyncService;
    private final LdapSyncAuditRepository auditRepository;
    private final LdapSyncLock syncLock;
    private final LdapManualSyncRunner runner;
    private final I18nService i18nService;

    /** 异步触发全量同步。 */
    public void triggerFullAsync() {
        start("full", ldapUserSyncService::runFullSync);
    }

    /** 异步触发增量同步。 */
    public void triggerIncrementalAsync() {
        start("incremental", ldapUserSyncService::runIncrementalSync);
    }

    /** 异步触发 Hermes AD 组全量同步。 */
    public void triggerHermesGroupFullAsync() {
        start("Hermes AD group full", ldapSyncService::runHermesAdGroupSync);
    }

    /** 异步触发 Hermes AD 组增量同步。 */
    public void triggerHermesGroupIncrementalAsync() {
        start("Hermes AD group incremental", ldapSyncService::runHermesAdGroupIncrementalSync);
    }

    /**
     * 拿到同步锁后交给 runner 异步执行，runner 结束时释放锁。锁不设最短持有时间：手动同步一结束，下一次就可以开始。
     * 锁库（Redis）连不上时报 503（{@code LDAP_SYNC_LOCK_UNAVAILABLE}），不在无锁状态下开跑。
     * 交不出去（线程池拒绝）时当场释放锁并把异常抛给调用方。
     */
    private void start(String type, Supplier<LdapSyncAudit> sync) {
        Optional<SimpleLock> acquired;
        try {
            acquired = syncLock.tryAcquire(Duration.ZERO);
        } catch (DataAccessException e) {
            throw new AdminServiceUnavailableException("LDAP_SYNC_LOCK_UNAVAILABLE",
                    i18nService.getMessage("admin.ldap.sync_lock_unavailable"), e);
        }
        SimpleLock lock = acquired.orElseThrow(() -> new AdminConflictException(
                "LDAP_SYNC_IN_PROGRESS", i18nService.getMessage("admin.ldap.sync_in_progress")));
        try {
            runner.run(type, sync, lock);
        } catch (RuntimeException e) {
            lock.unlock();
            throw e;
        }
    }

    /** 最近 20 条同步记录（状态总览）。 */
    @Transactional(readOnly = true)
    public List<LdapSyncAuditView> recentAudits() {
        return auditRepository.findTop20ByOrderByStartedAtDesc().stream()
                .map(LdapSyncAuditView::from)
                .toList();
    }

    /** 同步历史分页（时间倒序）。 */
    @Transactional(readOnly = true)
    public PageResult<LdapSyncAuditView> auditPage(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Page<LdapSyncAudit> result = auditRepository.findAllByOrderByStartedAtDesc(
                PageRequest.of(safePage, safeSize));
        List<LdapSyncAuditView> records = result.getContent().stream()
                .map(LdapSyncAuditView::from)
                .toList();
        return PageResult.of(records, safePage, safeSize, result.getTotalElements());
    }
}
