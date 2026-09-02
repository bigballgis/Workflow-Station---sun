package com.admin.ldap;

import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import com.platform.security.entity.Role;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.repository.RoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LdapSyncService} 单元测试：覆盖环境解析、组定义解析、memberOf 解析、虚拟组映射等核心逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LdapSyncService Hermes Group Sync")
class LdapSyncServiceTest {

    @Mock private LdapClient ldapClient;
    @Mock private LdapUserMapper ldapUserMapper;
    @Mock private LdapUserSyncService ldapUserSyncService;
    @Mock private LdapProperties ldapProperties;
    @Mock private LdapSyncAuditRepository auditRepository;
    @Mock private VirtualGroupRepository virtualGroupRepository;
    @Mock private VirtualGroupMemberRepository virtualGroupMemberRepository;
    @Mock private VirtualGroupRoleRepository virtualGroupRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RoleAssignmentRepository roleAssignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private Environment environment;
    @Mock private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @InjectMocks
    private LdapSyncService ldapSyncService;

    private LdapProperties.Attributes attributes;
    private LdapProperties.GroupSync groupSync;

    @BeforeEach
    void setUp() {
        attributes = new LdapProperties.Attributes();
        groupSync = new LdapProperties.GroupSync();
        lenient().when(ldapProperties.getAttributes()).thenReturn(attributes);
        lenient().when(ldapProperties.getGroupSync()).thenReturn(groupSync);
        lenient().when(ldapProperties.getHermesEnv()).thenReturn("");
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{});
        // Run transactional callbacks inline so membership-sync work executes in unit tests
        lenient().doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback =
                    inv.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        // 默认：显式映射未配置
        groupSync.setGroups("");
        groupSync.setRoles("");
        groupSync.setPattern("");
        groupSync.setBatchSize(20);
    }

    // ==================== 环境解析 ====================

    @Test
    @DisplayName("解析 Hermes 环境：hermes-env 配置优先于 spring profile")
    void resolveFromHermesEnvConfig() {
        when(ldapProperties.getHermesEnv()).thenReturn("UAT");
        assertEquals("UAT", ldapSyncService.resolveHermesEnvironment());
    }

    @Test
    @DisplayName("解析 Hermes 环境：未配置时回退 spring profile")
    void resolveFromSpringProfile() {
        when(ldapProperties.getHermesEnv()).thenReturn("");
        when(environment.getActiveProfiles()).thenReturn(new String[]{"uat"});
        assertEquals("UAT", ldapSyncService.resolveHermesEnvironment());
    }

    @Test
    @DisplayName("解析 Hermes 环境：无任何配置时默认 DEV")
    void resolveDefaultDev() {
        when(ldapProperties.getHermesEnv()).thenReturn("");
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        assertEquals("DEV", ldapSyncService.resolveHermesEnvironment());
    }

    // ==================== 组定义解析 ====================

    @Test
    @DisplayName("使用显式映射解析 AD 组")
    void explicitGroupMapping() {
        lenient().when(ldapProperties.getHermesEnv()).thenReturn("UAT");
        groupSync.setGroups("Admin=Infodir-Hermes-Default-UAT-Admin,User=Infodir-Hermes-Default-UAT-User,Developer=Infodir-Hermes-Default-UAT-Developer");

        Map<String, String> defs = ldapSyncService.resolveGroupDefinitions();
        assertEquals(3, defs.size());
        assertEquals("Infodir-Hermes-Default-UAT-Admin", defs.get("Admin"));
        assertEquals("Infodir-Hermes-Default-UAT-User", defs.get("User"));
        assertEquals("Infodir-Hermes-Default-UAT-Developer", defs.get("Developer"));
    }

    @Test
    @DisplayName("使用 roles + pattern 拼接 AD 组")
    void patternBasedMapping() {
        lenient().when(ldapProperties.getHermesEnv()).thenReturn("DEV");
        groupSync.setRoles("Admin,User,Developer,Supervisor");
        groupSync.setPattern("Infodir-Hermes-Default-{env}-{role}");

        Map<String, String> defs = ldapSyncService.resolveGroupDefinitions();
        assertEquals(3, defs.size());
        assertEquals("Infodir-Hermes-Default-DEV-Admin", defs.get("Admin"));
        assertEquals("Infodir-Hermes-Default-DEV-User", defs.get("User"));
        assertEquals("Infodir-Hermes-Default-DEV-Developer", defs.get("Developer"));
    }

    // ==================== memberOf CN 解析 ====================

    @Test
    @DisplayName("从 memberOf DN 提取 CN")
    void parseMemberOfCns() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("memberOf",
                "CN=Infodir-Hermes-Default-UAT-User,OU=Groups,DC=InfoDir,DC=Prod,DC=HSBC;" +
                "CN=Infodir-Hermes-Default-UAT-Developer,OU=Groups,DC=InfoDir,DC=Prod,DC=HSBC");

        Set<String> cns = ldapSyncService.parseMemberOfCns(attrs);
        assertEquals(2, cns.size());
        assertTrue(cns.contains("Infodir-Hermes-Default-UAT-User"));
        assertTrue(cns.contains("Infodir-Hermes-Default-UAT-Developer"));
    }

    @Test
    @DisplayName("memberOf 为空时返回空集合")
    void parseMemberOfEmpty() {
        Map<String, String> attrs = new HashMap<>();
        Set<String> cns = ldapSyncService.parseMemberOfCns(attrs);
        assertTrue(cns.isEmpty());
    }

    // ==================== 虚拟组绑定确保 ====================

    @Test
    @DisplayName("ensureBindings 创建缺失的角色和虚拟组")
    void ensureBindingsCreatesMissing() {
        Role mockRole = Role.builder().id("role-1").code("SYS_ADMIN").name("System Administrator").build();
        VirtualGroup mockVg = VirtualGroup.builder().id("vg-1").code("SYSTEM_ADMINISTRATORS").name("System Administrators").build();

        when(roleRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenReturn(mockRole);
        when(virtualGroupRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(virtualGroupRepository.save(any(VirtualGroup.class))).thenReturn(mockVg);
        when(virtualGroupRoleRepository.existsByVirtualGroupIdAndRoleId(anyString(), anyString())).thenReturn(false);
        when(virtualGroupRoleRepository.save(any())).thenReturn(null);
        when(roleAssignmentRepository.existsByRoleIdAndTargetTypeAndTargetId(anyString(), any(), anyString())).thenReturn(false);
        when(roleAssignmentRepository.save(any())).thenReturn(null);

        Map<String, String> groupDefs = Map.of(
                "Admin", "Infodir-Hermes-Default-DEV-Admin",
                "User", "Infodir-Hermes-Default-DEV-User",
                "Developer", "Infodir-Hermes-Default-DEV-Developer");

        // 不应抛异常
        ldapSyncService.ensureHermesBindings(groupDefs);
    }

    @Test
    @DisplayName("memberOf 命中 Admin/Developer AD 组时同步到对应多个系统虚拟组")
    void syncMembershipMapsAdminAndDeveloperGroupsToSystemVirtualGroups() {
        Map<String, VirtualGroup> groups = Map.of(
                "SYSTEM_ADMINISTRATORS", VirtualGroup.builder().id("vg-sys-admins").code("SYSTEM_ADMINISTRATORS").name("System Administrators").build(),
                "AUDITORS", VirtualGroup.builder().id("vg-auditors").code("AUDITORS").name("Auditors").build(),
                "TECH_LEADS", VirtualGroup.builder().id("vg-tech-leads").code("TECH_LEADS").name("Technical Leads").build(),
                "TEAM_LEADS", VirtualGroup.builder().id("vg-team-leads").code("TEAM_LEADS").name("Team Leads").build(),
                "DEVELOPERS", VirtualGroup.builder().id("vg-developers").code("DEVELOPERS").name("Developers").build(),
                "HERMES_DEFAULT_USERS", VirtualGroup.builder().id("vg-users").code("HERMES_DEFAULT_USERS").name("Hermes Default Users").build());
        when(virtualGroupRepository.findByCode(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(groups.get(invocation.getArgument(0))));
        when(virtualGroupMemberRepository.existsByGroupIdAndUserId(anyString(), anyString())).thenReturn(false);
        when(virtualGroupMemberRepository.findByUserId("user-1")).thenReturn(List.of());
        when(virtualGroupMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, String> attrs = new HashMap<>();
        attrs.put("memberOf",
                "CN=Infodir-Hermes-Default-UAT-Admin,OU=Groups,DC=InfoDir,DC=Prod,DC=HSBC;" +
                "CN=Infodir-Hermes-Default-UAT-Developer,OU=Groups,DC=InfoDir,DC=Prod,DC=HSBC");
        LdapSyncService.LdapGroupUserAccumulator accumulator = new LdapSyncService.LdapGroupUserAccumulator(attrs);
        accumulator.addHitGroup("Infodir-Hermes-Default-UAT-Admin", "Admin");
        accumulator.addHitGroup("Infodir-Hermes-Default-UAT-Developer", "Developer");
        ldapSyncService.syncVirtualGroupMemberships(
                List.of(Map.entry("45455063", accumulator)),
                Map.of(
                        "Admin", "Infodir-Hermes-Default-UAT-Admin",
                        "User", "Infodir-Hermes-Default-UAT-User",
                        "Developer", "Infodir-Hermes-Default-UAT-Developer"),
                Map.of("45455063", "user-1"));
        ArgumentCaptor<VirtualGroupMember> memberCaptor = ArgumentCaptor.forClass(VirtualGroupMember.class);
        verify(virtualGroupMemberRepository, times(5)).save(memberCaptor.capture());
        Set<String> savedGroupIds = memberCaptor.getAllValues().stream()
                .map(VirtualGroupMember::getGroupId)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("vg-sys-admins", "vg-auditors", "vg-tech-leads", "vg-team-leads", "vg-developers"), savedGroupIds);
    }

    @Test
    @DisplayName("AD group sync 补齐一层 EM/FM 用户且不递归、不写虚拟组成员")
    void hermesGroupSyncSupplementsDirectManagersOnly() throws Exception {
        groupSync.setGroups("User=Infodir-Hermes-Default-UAT-User");
        when(auditRepository.save(any(LdapSyncAudit.class))).thenAnswer(i -> i.getArgument(0));
        VirtualGroup usersVg = VirtualGroup.builder()
                .id("vg-users")
                .code("HERMES_DEFAULT_USERS")
                .name("Hermes Default Users")
                .build();
        when(virtualGroupRepository.findByCode(anyString())).thenReturn(Optional.of(usersVg));
        when(virtualGroupMemberRepository.findByGroupId(anyString())).thenReturn(List.of());
        when(virtualGroupMemberRepository.findByUserId(anyString())).thenReturn(List.of());
        when(virtualGroupMemberRepository.existsByGroupIdAndUserId(anyString(), anyString())).thenReturn(false);
        when(virtualGroupMemberRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Map<String, String> groupUser = ldapAttrs("U100", "User One");
        groupUser.put("memberOf", "CN=Infodir-Hermes-Default-UAT-User,OU=Groups,DC=example,DC=com");
        Map<String, String> entityManager = ldapAttrs("M200", "Entity Manager");
        Map<String, String> functionManager = ldapAttrs("M300", "Function Manager");
        when(ldapClient.fetchUsersInGroup("Infodir-Hermes-Default-UAT-User")).thenReturn(List.of(groupUser));
        when(userRepository.existsById("M200")).thenReturn(false);
        when(userRepository.existsById("M300")).thenReturn(false);
        when(ldapClient.getUserAttributesByEmployeeId("M200")).thenReturn(Optional.of(entityManager));
        when(ldapClient.getUserAttributesByEmployeeId("M300")).thenReturn(Optional.of(functionManager));
        when(ldapUserMapper.mapToUser(any())).thenAnswer(invocation -> {
            Map<String, String> attrs = invocation.getArgument(0);
            String employeeId = attrs.get("employeeID");
            LdapUserData.LdapUserDataBuilder builder = LdapUserData.builder()
                    .id(employeeId)
                    .employeeId(employeeId)
                    .username(employeeId)
                    .displayName(attrs.get("displayName"));
            if ("U100".equals(employeeId)) {
                builder.entityManagerId("M200").functionManagerId("M300");
            } else if ("M200".equals(employeeId)) {
                builder.entityManagerId("M201").functionManagerId("M202");
            } else if ("M300".equals(employeeId)) {
                builder.entityManagerId("M301").functionManagerId("M302");
            }
            return Optional.of(builder.build());
        });
        when(ldapUserSyncService.upsertUser(any())).thenAnswer(invocation -> {
            LdapUserData data = invocation.getArgument(0);
            return new LdapUserSyncService.UpsertResult(data.getId(), true);
        });
        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupSync();
        assertEquals("SUCCESS", audit.getStatus());
        assertEquals(3, audit.getUpserted());
        assertEquals(3, audit.getTotalFetched());
        ArgumentCaptor<LdapUserData> upsertCaptor = ArgumentCaptor.forClass(LdapUserData.class);
        verify(ldapUserSyncService, times(3)).upsertUser(upsertCaptor.capture());
        Set<String> upsertedEmployeeIds = upsertCaptor.getAllValues().stream()
                .map(LdapUserData::getId)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("U100", "M200", "M300"), upsertedEmployeeIds);
        verify(ldapClient, never()).getUserAttributesByEmployeeId(eq("M201"));
        verify(ldapClient, never()).getUserAttributesByEmployeeId(eq("M202"));
        verify(ldapClient, never()).getUserAttributesByEmployeeId(eq("M301"));
        verify(ldapClient, never()).getUserAttributesByEmployeeId(eq("M302"));
        verify(virtualGroupMemberRepository, times(1)).save(any(VirtualGroupMember.class));
    }
    @Test
    @DisplayName("已存在于 sys_users 的 EM/FM 不再从 LDAP 额外拉取")
    void supplementDirectManagersSkipsExistingUsers() throws Exception {
        Map<String, String> groupUser = ldapAttrs("U100", "User One");
        LdapSyncService.LdapGroupUserAccumulator accumulator = new LdapSyncService.LdapGroupUserAccumulator(groupUser);
        Map<String, LdapSyncService.LdapGroupUserAccumulator> users = new java.util.LinkedHashMap<>();
        users.put("U100", accumulator);
        when(ldapUserMapper.mapToUser(groupUser)).thenReturn(Optional.of(LdapUserData.builder()
                .id("U100")
                .employeeId("U100")
                .username("U100")
                .entityManagerId("M200")
                .functionManagerId("M300")
                .build()));
        when(userRepository.existsById("M200")).thenReturn(true);
        when(userRepository.existsById("M300")).thenReturn(true);
        int supplemented = ldapSyncService.supplementDirectManagers(users);
        assertEquals(0, supplemented);
        assertEquals(1, users.size());
        verify(ldapClient, never()).getUserAttributesByEmployeeId(anyString());
    }
    private Map<String, String> ldapAttrs(String employeeId, String displayName) {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("employeeID", employeeId);
        attrs.put("uid", employeeId);
        attrs.put("displayName", displayName);
        return attrs;
    }

    @Test
    @DisplayName("配置 whenChanged 时支持按用户变更做增量同步")
    void supportsUserChangeIncrementalSyncWhenWhenChangedConfigured() {
        attributes.setWhenChanged("whenChanged");
        assertTrue(ldapSyncService.supportsUserChangeIncrementalSync());
    }

    @Test
    @DisplayName("组 whenChanged 未变时仍拉取目标组成员并 upsert（Claudia：平台无此人）")
    void incrementalReconcilesGroupMembersWhenWhenChangedUnchanged() throws Exception {
        groupSync.setGroups("User=Infodir-PowerPlatform-WPBPP-DEV-User");
        stubIncrementalBaseline();
        VirtualGroup usersVg = VirtualGroup.builder()
                .id("vg-users").code("HERMES_DEFAULT_USERS").name("Hermes Default Users").build();
        when(virtualGroupRepository.findByCode(anyString())).thenReturn(Optional.of(usersVg));
        when(virtualGroupMemberRepository.existsByGroupIdAndUserId(anyString(), anyString())).thenReturn(false);
        when(virtualGroupMemberRepository.findByUserId(anyString())).thenReturn(List.of());
        when(virtualGroupMemberRepository.findByGroupId(anyString())).thenReturn(List.of());
        when(virtualGroupMemberRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Map<String, String> claudia = ldapAttrs("45455063", "Claudia");
        claudia.put("memberOf", "CN=Infodir-PowerPlatform-WPBPP-DEV-User,OU=Groups,DC=InfoDir,DC=Prod,DC=HSBC");
        when(ldapClient.fetchUsersInGroup("Infodir-PowerPlatform-WPBPP-DEV-User")).thenReturn(List.of(claudia));
        when(ldapUserMapper.mapToUser(any())).thenReturn(Optional.of(LdapUserData.builder()
                .id("45455063")
                .employeeId("45455063")
                .username("45455063")
                .displayName("Claudia")
                .build()));
        when(ldapUserSyncService.upsertUser(any()))
                .thenReturn(new LdapUserSyncService.UpsertResult("45455063", true));

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertEquals("SUCCESS", audit.getStatus());
        assertEquals(1, audit.getUpserted());
        assertEquals(1, audit.getInsertCount());
        verify(ldapClient).fetchUsersInGroup("Infodir-PowerPlatform-WPBPP-DEV-User");
        verify(ldapClient, never()).hasGroupChangedSince(anyString(), any());
        verify(ldapClient, never()).fetchUsersWithFilter(anyString());
        verify(ldapUserSyncService).upsertUser(any());
    }

    @Test
    @DisplayName("增量对账会覆盖已存在用户画像（平台改过也跟 LDAP）")
    void incrementalOverwritesExistingProfileFromLdap() throws Exception {
        groupSync.setGroups("User=Infodir-PowerPlatform-WPBPP-DEV-User");
        stubIncrementalBaseline();
        VirtualGroup usersVg = VirtualGroup.builder()
                .id("vg-users").code("HERMES_DEFAULT_USERS").name("Hermes Default Users").build();
        when(virtualGroupRepository.findByCode(anyString())).thenReturn(Optional.of(usersVg));
        when(virtualGroupMemberRepository.existsByGroupIdAndUserId(anyString(), anyString())).thenReturn(true);
        when(virtualGroupMemberRepository.findByUserId(anyString())).thenReturn(List.of());
        when(virtualGroupMemberRepository.findByGroupId(anyString())).thenReturn(List.of());
        Map<String, String> ldapUser = ldapAttrs("45455063", "LDAP Name");
        when(ldapClient.fetchUsersInGroup("Infodir-PowerPlatform-WPBPP-DEV-User")).thenReturn(List.of(ldapUser));
        when(ldapUserMapper.mapToUser(any())).thenReturn(Optional.of(LdapUserData.builder()
                .id("45455063")
                .employeeId("45455063")
                .username("45455063")
                .displayName("LDAP Name")
                .email("claudia@hsbc.com")
                .build()));
        when(ldapUserSyncService.upsertUser(any()))
                .thenReturn(new LdapUserSyncService.UpsertResult("45455063", false));

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertEquals("SUCCESS", audit.getStatus());
        assertEquals(1, audit.getUpdateCount());
        ArgumentCaptor<LdapUserData> captor = ArgumentCaptor.forClass(LdapUserData.class);
        verify(ldapUserSyncService).upsertUser(captor.capture());
        assertEquals("LDAP Name", captor.getValue().getDisplayName());
        assertEquals("claudia@hsbc.com", captor.getValue().getEmail());
    }

    @Test
    @DisplayName("已离开目标 AD 组的 LDAP 虚拟组成员会被摘掉，账号行不在本同步中删除")
    void incrementalPrunesLeaversFromHermesVirtualGroup() throws Exception {
        groupSync.setGroups("User=Infodir-PowerPlatform-WPBPP-DEV-User");
        stubIncrementalBaseline();
        VirtualGroup usersVg = VirtualGroup.builder()
                .id("vg-users").code("HERMES_DEFAULT_USERS").name("Hermes Default Users").build();
        when(virtualGroupRepository.findByCode(anyString())).thenReturn(Optional.of(usersVg));
        when(virtualGroupMemberRepository.existsByGroupIdAndUserId(anyString(), anyString())).thenReturn(false);
        when(virtualGroupMemberRepository.findByUserId(anyString())).thenReturn(List.of());
        when(virtualGroupMemberRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        VirtualGroupMember leaver = VirtualGroupMember.builder()
                .id("m-leaver")
                .groupId("vg-users")
                .userId("leaver-user")
                .addedBy("HERMES_AD_SYNC")
                .build();
        when(virtualGroupMemberRepository.findByGroupId("vg-users")).thenReturn(List.of(leaver));
        Map<String, String> stayer = ldapAttrs("11111111", "Stayer");
        when(ldapClient.fetchUsersInGroup("Infodir-PowerPlatform-WPBPP-DEV-User")).thenReturn(List.of(stayer));
        when(ldapUserMapper.mapToUser(any())).thenReturn(Optional.of(LdapUserData.builder()
                .id("11111111")
                .employeeId("11111111")
                .username("11111111")
                .displayName("Stayer")
                .build()));
        when(ldapUserSyncService.upsertUser(any()))
                .thenReturn(new LdapUserSyncService.UpsertResult("11111111", false));

        LdapSyncAudit audit = ldapSyncService.runHermesAdGroupIncrementalSync();

        assertEquals("SUCCESS", audit.getStatus());
        verify(virtualGroupMemberRepository).deleteByGroupIdAndUserId("vg-users", "leaver-user");
        verify(ldapUserSyncService, never()).upsertUser(org.mockito.ArgumentMatchers.argThat(
                data -> data != null && "leaver-user".equals(data.getId())));
    }

    private void stubIncrementalBaseline() {
        LdapSyncAudit baseline = LdapSyncAudit.builder()
                .id("audit-1")
                .syncType("HERMES_AD_GROUP")
                .status("SUCCESS")
                .snapshotAt(java.time.Instant.parse("2026-06-17T07:00:00Z"))
                .startedAt(java.time.Instant.parse("2026-06-17T07:00:00Z"))
                .build();
        when(auditRepository.findTopBySyncTypeInAndStatusOrderByStartedAtDesc(anyList(), anyString()))
                .thenReturn(Optional.of(baseline));
        when(auditRepository.save(any(LdapSyncAudit.class))).thenAnswer(i -> i.getArgument(0));
    }
}
