package com.admin.ldap;

import com.admin.repository.RoleRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import com.platform.security.entity.Role;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.repository.RoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
    @Mock private Environment environment;

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
        groupSync.setRoles("Admin,User,Developer");
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
        Role mockRole = Role.builder().id("role-1").code("HERMES_ADMIN").name("Hermes Admin").build();
        VirtualGroup mockVg = VirtualGroup.builder().id("vg-1").code("HERMES_ADMINS").name("Hermes Admins").build();

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
}
