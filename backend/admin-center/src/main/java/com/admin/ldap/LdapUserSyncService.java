package com.admin.ldap;

import com.admin.repository.UserRepository;
import com.platform.security.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * LDAP → {@code sys_users} 用户同步（全量 / 增量），LDAP 为权威源。
 *
 * <p>仅 {@code ldap.enabled=true} 时创建。落库策略：新用户写入不可登录占位密码（LDAP-only）；
 * 已存在用户仅更新画像字段，<b>不</b>覆盖其本地 {@code passwordHash}（避免锁死本地管理员）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = "enabled", havingValue = "true")
public class LdapUserSyncService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String TYPE_FULL = "FULL";
    private static final String TYPE_INCREMENTAL = "INCREMENTAL";
    /** AD generalized time（UTC），用于 whenChanged 增量过滤。 */
    private static final DateTimeFormatter AD_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final LdapClient ldapClient;
    private final LdapUserMapper ldapUserMapper;
    private final LdapProperties ldapProperties;
    private final UserRepository userRepository;
    private final LdapSyncAuditRepository auditRepository;
    private final PasswordEncoder passwordEncoder;

    /** 全量同步：拉取全部用户并 upsert。 */
    public LdapSyncAudit runFullSync() {
        LdapSyncAudit audit = startAudit(TYPE_FULL);
        try {
            List<Map<String, String>> rows = ldapClient.fetchAllUsers();
            applyRows(rows, audit);
            return finishSuccess(audit, rows.size());
        } catch (Exception e) {
            return finishFailed(audit, e);
        }
    }

    /** 增量同步：以最近成功记录的 snapshot 作为 whenChanged 水位；无基线则回退全量。 */
    public LdapSyncAudit runIncrementalSync() {
        Optional<Instant> watermark = auditRepository
                .findTopByStatusOrderByStartedAtDesc(STATUS_SUCCESS)
                .map(LdapSyncAudit::getSnapshotAt);
        if (watermark.isEmpty()) {
            log.info("No successful LDAP sync baseline; incremental falls back to full");
            return runFullSync();
        }
        LdapSyncAudit audit = startAudit(TYPE_INCREMENTAL);
        try {
            String filter = buildWhenChangedFilter(watermark.get());
            List<Map<String, String>> rows = ldapClient.fetchUsersWithFilter(filter);
            applyRows(rows, audit);
            return finishSuccess(audit, rows.size());
        } catch (Exception e) {
            return finishFailed(audit, e);
        }
    }

    /** 将一批 LDAP 行映射并逐个 upsert，统计成功/失败到审计对象。 */
    private void applyRows(List<Map<String, String>> rows, LdapSyncAudit audit) {
        int ok = 0;
        int failed = 0;
        for (Map<String, String> row : rows) {
            Optional<LdapUserData> mapped = ldapUserMapper.mapToUser(row);
            if (mapped.isEmpty()) {
                failed++;
                continue;
            }
            try {
                upsertUser(mapped.get());
                ok++;
            } catch (Exception e) {
                failed++;
                log.warn("LDAP upsert failed for id {}: {}", mapped.get().getId(), e.getMessage());
            }
        }
        audit.setUpserted(ok);
        audit.setFailed(failed);
    }

    /**
     * Upsert 单个用户。新用户写占位密码；已存在用户仅更新画像字段，保留其 passwordHash。
     *
     * @return {@code true} 为新插入，{@code false} 为已存在更新
     */
        @Transactional
    public boolean upsertUserReturningIsNew(LdapUserData data) {
        return upsertUser(data).isNew();
    }
    /**
     * Upsert 单个用户并返回落库后的真实 userId（用于登录后鉴权链路）。
     */
    @Transactional
    public String upsertUserReturningId(LdapUserData data) {
        return upsertUser(data).userId();
    }
    /**
     * Upsert 单个用户并返回真实 userId 与是否新建。
     */
    @Transactional
    public UpsertResult upsertUser(LdapUserData data) {
        return upsertUserReturningResult(data);
    }
    /**
     * Upsert 结果：返回真实 userId 与是否新建。
     *
     * <p>策略：
     * 1) 先按 employeeID（主键）匹配；
     * 2) 未命中时按 username 兜底合并（解决历史 UUID 本地用户与 LDAP 用户名重叠）；
     * 3) 仍未命中则新建 LDAP-only 用户。</p>
     */
    public UpsertResult upsertUserReturningResult(LdapUserData data) {
        Optional<User> existingById = userRepository.findById(data.getId());
        if (existingById.isPresent()) {
            User user = existingById.get();
            applyProfile(user, data, false);
            userRepository.save(user);
            return new UpsertResult(user.getId(), false);
        }
        String username = resolveUsername(data);
        Optional<User> existingByUsername = Optional.ofNullable(userRepository.findByUsername(username))
                .orElse(Optional.empty());
        if (existingByUsername.isPresent()) {
            User user = existingByUsername.get();
            applyProfile(user, data, false);
            userRepository.save(user);
            return new UpsertResult(user.getId(), false);
        }
        // 新用户：employeeID 作为 id；占位密码禁止本地登录
        User user = User.builder()
                .id(data.getId())
                .username(username)
                .passwordHash(ldapOnlyPlaceholderHash())
                .createdBy(LdapConstants.LDAP_SYNC_ACTOR)
                .build();
        applyProfile(user, data, true);
        userRepository.save(user);
        return new UpsertResult(user.getId(), true);
    }

    /** 写入画像字段（updatedBy=LDAP_SYNC_JOB）。{@code isNew} 时一并设置 username。 */
    private void applyProfile(User user, LdapUserData data, boolean isNew) {
        if (isNew) {
            user.setUsername(resolveUsername(data));
        }
        user.setEmail(data.getEmail());
        user.setDisplayName(truncate(data.getDisplayName(), 50));
        user.setFullName(truncate(data.getFullName(), 100));
        user.setPhone(truncate(data.getPhone(), 50));
        user.setEmployeeId(truncate(data.getEmployeeId(), 50));
        user.setPosition(truncate(data.getPosition(), 100));
        user.setEntityManagerId(data.getEntityManagerId());
        user.setFunctionManagerId(data.getFunctionManagerId());
        if (data.getStatus() != null) {
            user.setStatus(data.getStatus());
        }
        user.setUpdatedBy(LdapConstants.LDAP_SYNC_ACTOR);
        user.setDeleted(false);

        // Sync LDAP jpegPhoto → avatar (preserve existing photo if LDAP has none)
        if (data.getPhotoBase64() != null && !data.getPhotoBase64().isEmpty()) {
            try {
                user.setAvatar(java.util.Base64.getDecoder().decode(data.getPhotoBase64()));
            } catch (IllegalArgumentException e) {
                log.warn("Failed to decode jpegPhoto for user {} ({} bytes): {}",
                        user.getId(),
                        data.getPhotoBase64() != null ? data.getPhotoBase64().length() : 0,
                        e.getMessage());
            }
        }
    }

    private String resolveUsername(LdapUserData data) {
        return data.getUsername() != null ? data.getUsername() : data.getId();
    }

    private String ldapOnlyPlaceholderHash() {
        // 占位 hash 包含标记串；bcrypt 永不匹配，本地登录路径据此识别 LDAP-only 用户
        return passwordEncoder.encode(LdapConstants.LDAP_ONLY_AUTH_PLACEHOLDER + ":" + UUID.randomUUID());
    }

    private String buildWhenChangedFilter(Instant watermark) {
        String when = AD_TIME.format(watermark) + ".0Z";
        return "(" + ldapProperties.getAttributes().getWhenChanged() + ">=" + when + ")";
    }

    private LdapSyncAudit startAudit(String type) {
        LdapSyncAudit audit = LdapSyncAudit.builder()
                .id(UUID.randomUUID().toString())
                .syncType(type)
                .status(STATUS_RUNNING)
                .snapshotAt(Instant.now())
                .startedAt(Instant.now())
                .build();
        return auditRepository.save(audit);
    }

    private LdapSyncAudit finishSuccess(LdapSyncAudit audit, int totalFetched) {
        audit.setStatus(STATUS_SUCCESS);
        audit.setTotalFetched(totalFetched);
        audit.setFinishedAt(Instant.now());
        log.info("LDAP {} sync done: fetched={}, upserted={}, failed={}",
                audit.getSyncType(), totalFetched, audit.getUpserted(), audit.getFailed());
        return auditRepository.save(audit);
    }

    private LdapSyncAudit finishFailed(LdapSyncAudit audit, Exception e) {
        audit.setStatus(STATUS_FAILED);
        audit.setFinishedAt(Instant.now());
        audit.setMessage(truncate(e.getMessage(), 1000));
        log.error("LDAP {} sync failed: {}", audit.getSyncType(), e.getMessage());
        return auditRepository.save(audit);
    }

    private String truncate(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    public record UpsertResult(String userId, boolean isNew) {
    }
}
