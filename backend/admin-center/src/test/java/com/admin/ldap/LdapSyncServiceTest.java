package com.admin.ldap;
import com.admin.component.SecurityAuditComponent;
import com.admin.enums.AuditAction;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.platform.security.entity.User;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.model.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LdapSyncService}: each virtual group and team list follows the AD group configured on it, only AD
 * rows change, and the sync creates no group, role or binding (platform access model §5.6, D-29, AC-11).
 * The member table is a list-backed fake so each case asserts the rows it ends with.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LdapSyncService AD group sync")
class LdapSyncServiceTest {

    @Mock private LdapClient ldapClient;
    @Mock private LdapUserMapper ldapUserMapper;
    @Mock private LdapUserSyncService ldapUserSyncService;
    @Mock private LdapProperties ldapProperties;
    @Mock private LdapSyncAuditRepository auditRepository;
    @Mock private VirtualGroupRepository virtualGroupRepository;
    @Mock private VirtualGroupMemberRepository virtualGroupMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @Mock private SecurityAuditComponent securityAuditComponent;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private LdapSyncService ldapSyncService;

    private final List<VirtualGroupMember> rows = new ArrayList<>();
    private final List<VirtualGroup> groups = new ArrayList<>();

    @BeforeEach
    void setUp() {
        LdapProperties.GroupSync groupSync = new LdapProperties.GroupSync();
        groupSync.setBatchSize(20);
        lenient().when(ldapProperties.getAttributes()).thenReturn(new LdapProperties.Attributes());
        lenient().when(ldapProperties.getGroupSync()).thenReturn(groupSync);
        lenient().when(auditRepository.save(any(LdapSyncAudit.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().when(virtualGroupRepository.findAll()).thenReturn(groups);
        lenient().when(virtualGroupMemberRepository.findByGroupIdAndSource(anyString(), anyString()))
                .thenAnswer(inv -> rows.stream()
                        .filter(r -> r.getGroupId().equals(inv.getArgument(0))
                                && r.getSource().equals(inv.getArgument(1)))
                        .toList());
        lenient().when(virtualGroupMemberRepository.findByGroupId(anyString()))
                .thenAnswer(inv -> rows.stream().filter(r -> r.getGroupId().equals(inv.getArgument(0))).toList());
        lenient().when(virtualGroupMemberRepository.saveAll(anyIterable())).thenAnswer(inv -> {
            Iterable<VirtualGroupMember> added = inv.getArgument(0);
            added.forEach(rows::add);
            return added;
        });
        lenient().doAnswer(inv -> {
            Iterable<VirtualGroupMember> removed = inv.getArgument(0);
            removed.forEach(rows::remove);
            return null;
        }).when(virtualGroupMemberRepository).deleteAll(anyIterable());
        lenient().when(ldapUserMapper.mapToUser(any())).thenAnswer(inv -> {
            Map<String, String> attrs = inv.getArgument(0);
            if (attrs == null) {
                return Optional.empty(); // a test re-stubbing with when(...) calls this with null
            }
            String employeeId = attrs.get("employeeID");
            return Optional.of(LdapUserData.builder()
                    .id(employeeId).employeeId(employeeId).username(employeeId)
                    .displayName(attrs.get("displayName")).build());
        });
        lenient().when(ldapUserSyncService.upsertUser(any())).thenAnswer(inv -> {
            LdapUserData data = inv.getArgument(0);
            return data == null ? null : new LdapUserSyncService.UpsertResult(data.getId(), false);
        });
    }

    @Test
    @DisplayName("SYSTEM 与 CUSTOM 组的成员来自各自配置的 AD 组，记为 AD 来源")
    void systemAndCustomGroupsFollowTheirAdGroup() throws Exception {
        group("vg-sys-admins", "SYSTEM", "AD-ADMINS", null);
        group("vg-finance", "CUSTOM", "AD-FINANCE", null);
        adGroup("AD-ADMINS", "U1");
        adGroup("AD-FINANCE", "U2", "U3");

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertEquals("SUCCESS", audit.getStatus());
        assertThat(rowsOf("vg-sys-admins")).containsExactly("U1/MEMBER/AD");
        assertThat(rowsOf("vg-finance")).containsExactlyInAnyOrder("U2/MEMBER/AD", "U3/MEMBER/AD");
        assertThat(rows).allSatisfy(r -> assertEquals("HERMES_AD_SYNC", r.getAddedBy()));
    }

    @Test
    @DisplayName("团队两份名单各跟自己的 AD 组；同一 AD 组映射到多处只拉一次")
    void teamListsFollowTheirOwnAdGroupsAndEachAdGroupIsReadOnce() throws Exception {
        group("vg-team", "DEVELOPER", "AD-DEV", "AD-TL");
        group("vg-custom", "CUSTOM", "AD-DEV", null);
        adGroup("AD-DEV", "U1", "U2");
        adGroup("AD-TL", "U1");

        ldapSyncService.runHermesAdGroupSync();

        assertThat(rowsOf("vg-team")).containsExactlyInAnyOrder(
                "U1/DEVELOPER/AD", "U2/DEVELOPER/AD", "U1/TECH_LEAD/AD");
        assertThat(rowsOf("vg-custom")).containsExactlyInAnyOrder("U1/MEMBER/AD", "U2/MEMBER/AD");
        verify(ldapClient, times(1)).fetchUsersInGroup("AD-DEV");
        verify(ldapUserSyncService, times(2)).upsertUser(any());
    }

    @Test
    @DisplayName("离开 AD 组只删 AD 来源行；人工来源（含与 AD 并存的）保留；已有行不重复写")
    void leaversLoseOnlyTheirAdRows() throws Exception {
        group("vg-finance", "CUSTOM", "AD-FINANCE", null);
        row("vg-finance", "U1", "MEMBER", "AD");
        row("vg-finance", "U3", "MEMBER", "AD");
        row("vg-finance", "U3", "MEMBER", "MANUAL");
        row("vg-finance", "U4", "MEMBER", "MANUAL");
        adGroup("AD-FINANCE", "U1");

        ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf("vg-finance")).containsExactlyInAnyOrder(
                "U1/MEMBER/AD", "U3/MEMBER/MANUAL", "U4/MEMBER/MANUAL");
    }

    @Test
    @DisplayName("每条 AD 增删逐人写 Admin 操作审计，操作人 LDAP_SYNC_JOB；未变的人不写（D-32）")
    void eachAdChangeIsAuditedPerPerson() throws Exception {
        group("vg-team", "DEVELOPER", "AD-DEV", null);
        row("vg-team", "U1", "DEVELOPER", "AD");
        row("vg-team", "U3", "DEVELOPER", "AD");
        row("vg-team", "U3", "DEVELOPER", "MANUAL");
        row("vg-team", "U4", "TECH_LEAD", "AD");
        adGroup("AD-DEV", "U1", "U2");

        ldapSyncService.runHermesAdGroupIncrementalSync();

        ArgumentCaptor<SecurityAuditComponent.AuditLogRequest> captor =
                ArgumentCaptor.forClass(SecurityAuditComponent.AuditLogRequest.class);
        verify(securityAuditComponent, times(3)).recordAudit(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(r -> {
                    assertEquals("VIRTUAL_GROUP", r.getResourceType());
                    assertEquals("vg-team", r.getResourceId());
                    assertEquals("LDAP_SYNC_JOB", r.getUserId());
                    assertEquals("LDAP_SYNC_JOB", r.getUserName());
                })
                .extracting(r -> r.getAction() + " " + r.getNewValue())
                .containsExactlyInAnyOrder(
                        "CREATE {\"userId\":\"U2\",\"listType\":\"DEVELOPER\",\"source\":\"AD\"}",
                        "DELETE {\"userId\":\"U3\",\"listType\":\"DEVELOPER\",\"source\":\"AD\"}",
                        "DELETE {\"userId\":\"U4\",\"listType\":\"TECH_LEAD\",\"source\":\"AD\"}");
    }

    @Test
    @DisplayName("读不出来的 AD 组：用到它的名单不加不删，其余照常；记录为部分成功")
    void anUnreadableAdGroupLeavesItsListsAlone() throws Exception {
        group("vg-finance", "CUSTOM", "AD-FINANCE", null);
        group("vg-ops", "CUSTOM", "AD-OPS", null);
        row("vg-finance", "U1", "MEMBER", "AD");
        when(ldapClient.fetchUsersInGroup("AD-FINANCE")).thenThrow(new NamingException("timeout"));
        adGroup("AD-OPS", "U2");

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertEquals("PARTIAL", audit.getStatus());
        assertEquals("Not read, lists unchanged: AD-FINANCE", audit.getMessage());
        assertThat(rowsOf("vg-finance")).containsExactly("U1/MEMBER/AD");
        assertThat(rowsOf("vg-ops")).containsExactly("U2/MEMBER/AD");
    }

    @Test
    @DisplayName("AD 完全不可达（一个已映射的 AD 组都读不出）：记录为失败，名单都不动")
    void anUnreachableDirectoryFailsTheRunAndChangesNothing() throws Exception {
        group("vg-finance", "CUSTOM", "AD-FINANCE", null);
        group("vg-ops", "CUSTOM", "AD-OPS", null);
        row("vg-finance", "U1", "MEMBER", "AD");
        row("vg-ops", "U2", "MEMBER", "AD");
        when(ldapClient.fetchUsersInGroup(anyString())).thenThrow(new NamingException("Connection refused"));

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertEquals("FAILED", audit.getStatus());
        assertEquals("Not read, lists unchanged: AD-FINANCE, AD-OPS", audit.getMessage());
        assertThat(rowsOf("vg-finance")).containsExactly("U1/MEMBER/AD");
        assertThat(rowsOf("vg-ops")).containsExactly("U2/MEMBER/AD");
    }

    @Test
    @DisplayName("找不到的 AD 组返回空：AD 成员全部删除，人工成员保留（C-3 已接受）")
    void aMissingAdGroupRemovesItsAdMembers() throws Exception {
        group("vg-finance", "CUSTOM", "AD-TYPO", null);
        row("vg-finance", "U1", "MEMBER", "AD");
        row("vg-finance", "U2", "MEMBER", "MANUAL");
        when(ldapClient.fetchUsersInGroup("AD-TYPO")).thenReturn(new LdapClient.AdGroupMembers(List.of(), true));

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf("vg-finance")).containsExactly("U2/MEMBER/MANUAL");
        assertEquals("SUCCESS", audit.getStatus()); // an empty answer is a read, not a failure (C-3)
        assertEquals("No members found (missing or empty AD group): AD-TYPO", audit.getMessage());
    }

    @Test
    @DisplayName("没有 AD 映射的名单与本组类型之外的 AD 行被删除，人工行保留（D-29）")
    void listsWithoutMappingLoseTheirAdRows() throws Exception {
        group("vg-team", "DEVELOPER", "AD-DEV", null);
        group("vg-custom", "CUSTOM", null, null);
        row("vg-team", "U1", "DEVELOPER", "AD");
        row("vg-team", "U2", "TECH_LEAD", "AD");
        row("vg-team", "U3", "MEMBER", "AD");
        row("vg-team", "U4", "TECH_LEAD", "MANUAL");
        row("vg-custom", "U5", "MEMBER", "AD");
        row("vg-custom", "U6", "MEMBER", "MANUAL");
        adGroup("AD-DEV", "U1");

        ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf("vg-team")).containsExactlyInAnyOrder("U1/DEVELOPER/AD", "U4/TECH_LEAD/MANUAL");
        assertThat(rowsOf("vg-custom")).containsExactly("U6/MEMBER/MANUAL");
    }

    @Test
    @DisplayName("没有任何映射：不访问 AD，AD 行清空，审计写明原因")
    void noMappingAtAllTouchesNoDirectory() throws Exception {
        group("vg-sys-admins", "SYSTEM", null, null);
        row("vg-sys-admins", "U1", "MEMBER", "AD");
        row("vg-sys-admins", "U2", "MEMBER", "MANUAL");

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertEquals("SUCCESS", audit.getStatus());
        assertEquals("No virtual group maps an AD group", audit.getMessage());
        assertThat(rowsOf("vg-sys-admins")).containsExactly("U2/MEMBER/MANUAL");
        verify(ldapClient, never()).fetchUsersInGroup(anyString());
    }

    @Test
    @DisplayName("有成员写画像失败：该 AD 组的名单只补不删")
    void aMemberThatCouldNotBeStoredKeepsLeavers() throws Exception {
        group("vg-finance", "CUSTOM", "AD-FINANCE", null);
        row("vg-finance", "U9", "MEMBER", "AD");
        adGroup("AD-FINANCE", "U1", "U5");
        when(ldapUserSyncService.upsertUser(any())).thenAnswer(inv -> {
            LdapUserData data = inv.getArgument(0);
            if ("U5".equals(data.getId())) {
                throw new IllegalStateException("constraint");
            }
            return new LdapUserSyncService.UpsertResult(data.getId(), false);
        });

        ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf("vg-finance")).containsExactlyInAnyOrder("U1/MEMBER/AD", "U9/MEMBER/AD");
    }

    @Test
    @DisplayName("同步不创建、不修改任何虚拟组（AC-11）")
    void syncCreatesNoGroup() throws Exception {
        group("vg-team", "DEVELOPER", "AD-DEV", "AD-TL");
        adGroup("AD-DEV", "U1");
        adGroup("AD-TL", "U2");

        ldapSyncService.runHermesAdGroupSync();

        verify(virtualGroupRepository, never()).save(any());
        verify(virtualGroupRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("AD group sync 补齐一层 EM/FM 用户且不递归、不进名单")
    void syncSupplementsDirectManagersOnly() throws Exception {
        group("vg-users", "CUSTOM", "AD-USERS", null);
        Map<String, String> groupUser = ldapAttrs("U100", "User One");
        when(ldapClient.fetchUsersInGroup("AD-USERS"))
                .thenReturn(new LdapClient.AdGroupMembers(List.of(groupUser), true));
        when(userRepository.existsById("M200")).thenReturn(false);
        when(userRepository.existsById("M300")).thenReturn(false);
        when(ldapClient.getUserAttributesByEmployeeId("M200")).thenReturn(Optional.of(ldapAttrs("M200", "Entity Manager")));
        when(ldapClient.getUserAttributesByEmployeeId("M300")).thenReturn(Optional.of(ldapAttrs("M300", "Function Manager")));
        when(ldapUserMapper.mapToUser(any())).thenAnswer(invocation -> {
            Map<String, String> attrs = invocation.getArgument(0);
            String employeeId = attrs.get("employeeID");
            LdapUserData.LdapUserDataBuilder builder = LdapUserData.builder()
                    .id(employeeId).employeeId(employeeId).username(employeeId)
                    .displayName(attrs.get("displayName"));
            if ("U100".equals(employeeId)) {
                builder.entityManagerId("M200").functionManagerId("M300");
            } else if ("M200".equals(employeeId)) {
                builder.entityManagerId("M201").functionManagerId("M202");
            }
            return Optional.of(builder.build());
        });

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupSync();

        assertEquals("SUCCESS", audit.getStatus());
        assertEquals(3, audit.getUpserted());
        assertEquals(3, audit.getTotalFetched());
        ArgumentCaptor<LdapUserData> upsertCaptor = ArgumentCaptor.forClass(LdapUserData.class);
        verify(ldapUserSyncService, times(3)).upsertUser(upsertCaptor.capture());
        assertThat(upsertCaptor.getAllValues()).extracting(LdapUserData::getId)
                .containsExactlyInAnyOrder("U100", "M200", "M300");
        verify(ldapClient, never()).getUserAttributesByEmployeeId(eq("M201"));
        verify(ldapClient, never()).getUserAttributesByEmployeeId(eq("M202"));
        assertThat(rowsOf("vg-users")).containsExactly("U100/MEMBER/AD");
    }

    @Test
    @DisplayName("已存在于 sys_users 的 EM/FM 不再从 LDAP 额外拉取")
    void supplementDirectManagersSkipsExistingUsers() throws Exception {
        Map<String, String> groupUser = ldapAttrs("U100", "User One");
        Map<String, LdapSyncService.LdapGroupUserAccumulator> users = new java.util.LinkedHashMap<>();
        users.put("U100", new LdapSyncService.LdapGroupUserAccumulator(groupUser));
        when(ldapUserMapper.mapToUser(groupUser)).thenReturn(Optional.of(LdapUserData.builder()
                .id("U100").employeeId("U100").username("U100")
                .entityManagerId("M200").functionManagerId("M300").build()));
        when(userRepository.existsById("M200")).thenReturn(true);
        when(userRepository.existsById("M300")).thenReturn(true);

        int supplemented = ldapSyncService.supplementDirectManagers(users);

        assertEquals(0, supplemented);
        assertEquals(1, users.size());
        verify(ldapClient, never()).getUserAttributesByEmployeeId(anyString());
    }

    @Test
    @DisplayName("已是人工成员的人出现在 AD 组里：补一条 AD 行，两种来源并存（AC-09 的前提）")
    void aManualMemberAlsoGetsAnAdRow() throws Exception {
        group("vg-finance", "CUSTOM", "AD-FINANCE", null);
        row("vg-finance", "U1", "MEMBER", "MANUAL");
        adGroup("AD-FINANCE", "U1");

        ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf("vg-finance")).containsExactlyInAnyOrder("U1/MEMBER/MANUAL", "U1/MEMBER/AD");
    }

    @Test
    @DisplayName("AD 组只读到一部分成员：只补不删，审计写明")
    void aPartlyReadAdGroupOnlyAdds() throws Exception {
        group("vg-finance", "CUSTOM", "AD-FINANCE", null);
        row("vg-finance", "U9", "MEMBER", "AD");
        when(ldapClient.fetchUsersInGroup("AD-FINANCE"))
                .thenReturn(new LdapClient.AdGroupMembers(List.of(ldapAttrs("U1", "User U1")), false));

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf("vg-finance")).containsExactlyInAnyOrder("U1/MEMBER/AD", "U9/MEMBER/AD");
        assertEquals("PARTIAL", audit.getStatus());
        assertEquals("Read incompletely, nobody removed: AD-FINANCE", audit.getMessage());
    }

    @Test
    @DisplayName("一个人在两个 AD 组里且写画像失败：两个组都只补不删")
    void aFailedUpsertHoldsBackEveryGroupOfThatUser() throws Exception {
        group("vg-a", "CUSTOM", "AD-A", null);
        group("vg-b", "CUSTOM", "AD-B", null);
        row("vg-a", "U8", "MEMBER", "AD");
        row("vg-b", "U9", "MEMBER", "AD");
        adGroup("AD-A", "U1", "U5");
        adGroup("AD-B", "U5");
        when(ldapUserSyncService.upsertUser(any())).thenAnswer(inv -> {
            LdapUserData data = inv.getArgument(0);
            if ("U5".equals(data.getId())) {
                throw new IllegalStateException("constraint");
            }
            return new LdapUserSyncService.UpsertResult(data.getId(), false);
        });

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf("vg-a")).containsExactlyInAnyOrder("U1/MEMBER/AD", "U8/MEMBER/AD");
        assertThat(rowsOf("vg-b")).containsExactly("U9/MEMBER/AD");
        assertEquals("PARTIAL", audit.getStatus());
        assertEquals("Read incompletely, nobody removed: AD-A, AD-B", audit.getMessage());
        assertEquals(1, audit.getFailed()); // U5 could not be stored
        assertEquals(0, audit.getSkippedMissingKey());
    }

    @Test
    @DisplayName("AD 组里没有员工编号的成员：记为跳过并计数，不算失败，不挡移除，状态仍成功")
    void membersWithoutAnEmployeeIdAreSkippedAndCounted() throws Exception {
        group("vg-finance", "CUSTOM", "AD-FINANCE", null);
        row("vg-finance", "U9", "MEMBER", "AD");
        Map<String, String> serviceAccount = new HashMap<>();
        serviceAccount.put("uid", "svc-report");
        when(ldapClient.fetchUsersInGroup("AD-FINANCE")).thenReturn(new LdapClient.AdGroupMembers(
                List.of(ldapAttrs("U1", "User U1"), serviceAccount), true));

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf("vg-finance")).containsExactly("U1/MEMBER/AD"); // U9 left AD-FINANCE: still removed
        assertEquals("SUCCESS", audit.getStatus());
        assertEquals(2, audit.getTotalFetched());
        assertEquals(1, audit.getSkippedMissingKey());
        assertEquals(0, audit.getFailed());
        assertEquals("Skipped without an employee ID (not stored): 1", audit.getMessage());
        verify(ldapUserSyncService, times(1)).upsertUser(any());
    }

    @Test
    @DisplayName("Public 没有名单：即使配了 AD 组也不拉取，AD 行删除")
    void publicHasNoLists() throws Exception {
        group(VirtualGroup.PUBLIC_TEAM_ID, "DEVELOPER", "AD-EVERYONE", null);
        row(VirtualGroup.PUBLIC_TEAM_ID, "U1", "DEVELOPER", "AD");

        ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf(VirtualGroup.PUBLIC_TEAM_ID)).isEmpty();
        verify(ldapClient, never()).fetchUsersInGroup(anyString());
    }

    @Test
    @DisplayName("同步不让 System Administrators 失去最后一名有效成员（D-31）")
    void systemAdministratorsKeepsItsLastActiveMember() throws Exception {
        systemAdministrators("AD-ADMINS");
        row("vg-sys-admins", "U1", "MEMBER", "AD");
        when(ldapClient.fetchUsersInGroup("AD-ADMINS")).thenReturn(new LdapClient.AdGroupMembers(List.of(), true));
        users(active("U1"));

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf("vg-sys-admins")).containsExactly("U1/MEMBER/AD");
        assertEquals("SUCCESS", audit.getStatus()); // the AD group was read; keeping the last admin is not a read failure
        assertEquals("Kept the AD members of SYSTEM_ADMINISTRATORS: removing them would leave no active member; "
                + "No members found (missing or empty AD group): AD-ADMINS", audit.getMessage());
        verify(securityAuditComponent, never()).recordAudit(any());
    }

    @Test
    @DisplayName("还有有效的人工管理员时，System Administrators 的 AD 成员照常删除")
    void systemAdministratorsLosesAdMembersWhileAManualAdminStays() throws Exception {
        systemAdministrators("AD-ADMINS");
        row("vg-sys-admins", "U1", "MEMBER", "AD");
        row("vg-sys-admins", "U2", "MEMBER", "MANUAL");
        when(ldapClient.fetchUsersInGroup("AD-ADMINS")).thenReturn(new LdapClient.AdGroupMembers(List.of(), true));
        users(active("U2"));

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf("vg-sys-admins")).containsExactly("U2/MEMBER/MANUAL");
        assertEquals("No members found (missing or empty AD group): AD-ADMINS", audit.getMessage());
    }

    @Test
    @DisplayName("只剩停用账号的人工管理员不算有效成员，AD 成员保留")
    void aLockedManualAdminDoesNotCount() throws Exception {
        systemAdministrators("AD-ADMINS");
        row("vg-sys-admins", "U1", "MEMBER", "AD");
        row("vg-sys-admins", "U2", "MEMBER", "MANUAL");
        when(ldapClient.fetchUsersInGroup("AD-ADMINS")).thenReturn(new LdapClient.AdGroupMembers(List.of(), true));
        User locked = active("U2");
        locked.setStatus(UserStatus.LOCKED);
        users(locked);

        ldapSyncService.runHermesAdGroupIncrementalSync();

        assertThat(rowsOf("vg-sys-admins")).containsExactlyInAnyOrder("U1/MEMBER/AD", "U2/MEMBER/MANUAL");
    }

    @Test
    @DisplayName("同步覆盖已存在用户的画像（平台改过也跟 LDAP）")
    void syncOverwritesExistingProfileFromLdap() throws Exception {
        group("vg-users", "CUSTOM", "AD-USERS", null);
        when(ldapClient.fetchUsersInGroup("AD-USERS"))
                .thenReturn(new LdapClient.AdGroupMembers(List.of(ldapAttrs("U200", "LDAP Name")), true));
        when(ldapUserMapper.mapToUser(any())).thenReturn(Optional.of(LdapUserData.builder()
                .id("U200").employeeId("U200").username("U200")
                .displayName("LDAP Name").email("user@example.com").build()));

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertEquals("SUCCESS", audit.getStatus());
        assertEquals(1, audit.getUpdateCount());
        ArgumentCaptor<LdapUserData> captor = ArgumentCaptor.forClass(LdapUserData.class);
        verify(ldapUserSyncService).upsertUser(captor.capture());
        assertEquals("LDAP Name", captor.getValue().getDisplayName());
        assertEquals("user@example.com", captor.getValue().getEmail());
    }

    private void group(String id, String type, String adGroup, String techLeadAdGroup) {
        groups.add(VirtualGroup.builder().id(id).code(id.toUpperCase()).name(id).type(type)
                .adGroup(adGroup).techLeadAdGroup(techLeadAdGroup).build());
    }

    private void systemAdministrators(String adGroup) {
        groups.add(VirtualGroup.builder().id("vg-sys-admins").code(VirtualGroup.SYSTEM_ADMINISTRATORS_CODE)
                .name("System Administrators").type("SYSTEM").adGroup(adGroup).build());
    }

    private static User active(String id) {
        return User.builder().id(id).username(id).status(UserStatus.ACTIVE).deleted(false).build();
    }

    private void users(User... users) {
        when(userRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<String> ids = inv.getArgument(0);
            Set<String> wanted = new java.util.HashSet<>();
            ids.forEach(wanted::add);
            return java.util.Arrays.stream(users).filter(u -> wanted.contains(u.getId())).toList();
        });
    }

    private void row(String groupId, String userId, String listType, String source) {
        rows.add(VirtualGroupMember.builder().id(groupId + "/" + userId + "/" + listType + "/" + source)
                .groupId(groupId).userId(userId).listType(listType).source(source)
                .addedBy("AD".equals(source) ? "HERMES_AD_SYNC" : "system").build());
    }

    private void adGroup(String cn, String... employeeIds) throws NamingException {
        List<Map<String, String>> users = new ArrayList<>();
        for (String employeeId : employeeIds) {
            users.add(ldapAttrs(employeeId, "User " + employeeId));
        }
        when(ldapClient.fetchUsersInGroup(cn)).thenReturn(new LdapClient.AdGroupMembers(users, true));
    }

    /** A list, not a set, so a row written twice shows up. */
    private List<String> rowsOf(String groupId) {
        return rows.stream()
                .filter(r -> r.getGroupId().equals(groupId))
                .map(r -> r.getUserId() + "/" + r.getListType() + "/" + r.getSource())
                .collect(Collectors.toList());
    }

    private Map<String, String> ldapAttrs(String employeeId, String displayName) {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("employeeID", employeeId);
        attrs.put("uid", employeeId);
        attrs.put("displayName", displayName);
        return attrs;
    }
}
