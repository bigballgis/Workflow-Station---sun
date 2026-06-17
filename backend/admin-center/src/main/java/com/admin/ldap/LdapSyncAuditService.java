package com.admin.ldap;

import com.admin.dto.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * LDAP 同步的查询与异步触发服务（控制器与同步实现/仓库之间的编排层）。
 *
 * <p>手动触发采用 {@link Async} 异步执行，避免长同步阻塞 HTTP 请求；调用方通过审计接口轮询进度。
 * 仅 {@code ldap.enabled=true} 时创建。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = "enabled", havingValue = "true")
public class LdapSyncAuditService {

    private final LdapUserSyncService ldapUserSyncService;
    private final LdapSyncAuditRepository auditRepository;

    /** 异步触发全量同步。 */
    @Async
    public void triggerFullAsync() {
        try {
            ldapUserSyncService.runFullSync();
        } catch (Exception e) {
            log.error("Async full LDAP sync error: {}", e.getMessage());
        }
    }

    /** 异步触发增量同步。 */
    @Async
    public void triggerIncrementalAsync() {
        try {
            ldapUserSyncService.runIncrementalSync();
        } catch (Exception e) {
            log.error("Async incremental LDAP sync error: {}", e.getMessage());
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
