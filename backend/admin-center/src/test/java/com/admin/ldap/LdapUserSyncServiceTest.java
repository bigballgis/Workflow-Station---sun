package com.admin.ldap;

import com.admin.repository.UserRepository;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link LdapUserSyncService} 单元测试（Mockito）。
 *
 * <p>重点：①全量同步审计统计；②新用户写入不可登录占位密码；
 * ③已存在用户仅更新画像、<b>保留</b>本地 passwordHash（不锁死本地管理员）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LdapUserSyncService 同步与 upsert")
class LdapUserSyncServiceTest {

    @Mock private LdapClient ldapClient;
    @Mock private UserRepository userRepository;
    @Mock private LdapSyncAuditRepository auditRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private LdapUserSyncService service;

    @BeforeEach
    void setUp() {
        LdapProperties props = new LdapProperties();
        LdapUserMapper mapper = new LdapUserMapper(props);
        service = new LdapUserSyncService(
                ldapClient, mapper, props, userRepository, auditRepository, passwordEncoder);
    }

    private Map<String, String> row(String empId, String uid, String display) {
        Map<String, String> m = new HashMap<>();
        m.put("employeeID", empId);
        m.put("uid", uid);
        m.put("displayName", display);
        m.put("mail", uid + "@example.org");
        return m;
    }

    @Test
    @DisplayName("全量同步：新用户占位密码 + 已存在用户保留本地 hash，审计 SUCCESS")
    void fullSyncUpsertsAndPreservesLocalHash() throws Exception {
        when(ldapClient.fetchAllUsers()).thenReturn(List.of(
                row("100001", "alice", "Alice Anderson"),   // 已存在
                row("100002", "bob", "Bob Brown")));          // 新用户

        // 审计与保存：返回入参
        when(auditRepository.save(any(LdapSyncAudit.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("ENC_PLACEHOLDER");

        User existing = User.builder()
                .id("100001").username("alice").passwordHash("LOCAL_BCRYPT_HASH")
                .status(UserStatus.ACTIVE).build();
        when(userRepository.findById("100001")).thenReturn(Optional.of(existing));
        when(userRepository.findById("100002")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());

        LdapSyncAudit audit = service.runFullSync();

        assertThat(audit.getStatus()).isEqualTo("SUCCESS");
        assertThat(audit.getSyncType()).isEqualTo("FULL");
        assertThat(audit.getTotalFetched()).isEqualTo(2);
        assertThat(audit.getUpserted()).isEqualTo(2);
        assertThat(audit.getFailed()).isEqualTo(0);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.times(2)).save(saved.capture());

        User aliceSaved = saved.getAllValues().stream()
                .filter(u -> "100001".equals(u.getId())).findFirst().orElseThrow();
        User bobSaved = saved.getAllValues().stream()
                .filter(u -> "100002".equals(u.getId())).findFirst().orElseThrow();

        // 已存在用户：保留本地 hash，画像更新
        assertThat(aliceSaved.getPasswordHash()).isEqualTo("LOCAL_BCRYPT_HASH");
        assertThat(aliceSaved.getDisplayName()).isEqualTo("Alice Anderson");
        assertThat(aliceSaved.getUpdatedBy()).isEqualTo(LdapConstants.LDAP_SYNC_ACTOR);

        // 新用户：占位密码 + createdBy 标记
        assertThat(bobSaved.getPasswordHash()).isEqualTo("ENC_PLACEHOLDER");
        assertThat(bobSaved.getCreatedBy()).isEqualTo(LdapConstants.LDAP_SYNC_ACTOR);
        assertThat(bobSaved.getUsername()).isEqualTo("bob");
    }

    @Test
    @DisplayName("无 employeeID 的行计入 failed，不调用 save")
    void rowWithoutEmployeeIdCountedAsFailed() throws Exception {
        Map<String, String> bad = new HashMap<>();
        bad.put("uid", "ghost");
        when(ldapClient.fetchAllUsers()).thenReturn(List.of(bad));
        when(auditRepository.save(any(LdapSyncAudit.class))).thenAnswer(i -> i.getArgument(0));

        LdapSyncAudit audit = service.runFullSync();

        assertThat(audit.getStatus()).isEqualTo("SUCCESS");
        assertThat(audit.getUpserted()).isEqualTo(0);
        assertThat(audit.getFailed()).isEqualTo(1);
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }

    @Test
    @DisplayName("增量同步无成功基线时回退全量")
    void incrementalFallsBackToFullWhenNoBaseline() throws Exception {
        when(auditRepository.findTopByStatusOrderByStartedAtDesc("SUCCESS"))
                .thenReturn(Optional.empty());
        when(ldapClient.fetchAllUsers()).thenReturn(List.of());
        when(auditRepository.save(any(LdapSyncAudit.class))).thenAnswer(i -> i.getArgument(0));

        LdapSyncAudit audit = service.runIncrementalSync();

        assertThat(audit.getSyncType()).isEqualTo("FULL");
        assertThat(audit.getStatus()).isEqualTo("SUCCESS");
        org.mockito.Mockito.verify(ldapClient, org.mockito.Mockito.never())
                .fetchUsersWithFilter(eq(""));
    }

    @Test
    @DisplayName("按用户名命中历史本地账号时合并并返回真实数据库ID")
    void upsertByUsernameFallbackReturnsPersistedId() {
        LdapUserData ldapUser = LdapUserData.builder()
                .id("45455063")
                .employeeId("45455063")
                .username("45455063")
                .displayName("Liam")
                .build();
        User local = User.builder()
                .id("7cb77cda-f836-47c7-84ef-c4ac9d7e95fe")
                .username("45455063")
                .passwordHash("LOCAL_HASH")
                .status(UserStatus.ACTIVE)
                .build();
        when(userRepository.findById("45455063")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("45455063")).thenReturn(Optional.of(local));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        String persistedId = service.upsertUserReturningId(ldapUser);
        assertThat(persistedId).isEqualTo("7cb77cda-f836-47c7-84ef-c4ac9d7e95fe");
        assertThat(local.getEmployeeId()).isEqualTo("45455063");
        assertThat(local.getDisplayName()).isEqualTo("Liam");
    }
}
