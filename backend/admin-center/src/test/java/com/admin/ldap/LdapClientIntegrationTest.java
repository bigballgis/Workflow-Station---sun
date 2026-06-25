package com.admin.ldap;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * {@link LdapClient} 集成测试：用 UnboundID 内存 LDAP 目录验证 JNDI 连接、DN 搜索、bind 校验、
 * 分页全量拉取与按 DN 取属性。属性名映射切换为标准 inetOrgPerson schema（与本地 mock 一致）。
 */
@DisplayName("LdapClient（UnboundID 内存目录）")
class LdapClientIntegrationTest {
    private static final String BASE_DN = "dc=example,dc=org";
    private static final String ADMIN_DN = "cn=admin,dc=example,dc=org";
    private static final String ADMIN_PW = "admin";
    private InMemoryDirectoryServer ds;
    private LdapClient ldapClient;
    @BeforeEach
    void setUp() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
        config.addAdditionalBindCredentials(ADMIN_DN, ADMIN_PW);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", 0));
        config.setSchema(null); // 关闭严格 schema 校验，便于注入测试条目
        ds = new InMemoryDirectoryServer(config);
        ds.startListening();
        seed();
        ldapClient = new LdapClient(buildProps(ds.getListenPort()));
    }
    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.shutDown(true);
        }
    }
    private void seed() throws Exception {
        ds.add("dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: example");
        ds.add("dn: ou=people," + BASE_DN, "objectClass: top", "objectClass: organizationalUnit", "ou: people");
        ds.add("dn: uid=alice,ou=people," + BASE_DN,
                "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: alice", "cn: Alice Anderson", "sn: Anderson", "givenName: Alice",
                "displayName: Alice Anderson", "employeeNumber: 100001", "mail: alice@example.org",
                "telephoneNumber: +85212340001", "title: Engineering Manager", "userPassword: alicepw");
        ds.add("dn: uid=bob,ou=people," + BASE_DN,
                "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: bob", "cn: Bob Brown", "sn: Brown", "givenName: Bob",
                "displayName: Bob Brown", "employeeNumber: 100002", "mail: bob@example.org",
            "memberOf: cn=Hermes Users,ou=groups," + BASE_DN,
                "userPassword: bobpw");
        ds.add("dn: uid=carol,ou=people," + BASE_DN,
                "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: carol", "cn: Carol Clarke", "sn: Clarke", "givenName: Carol",
                "displayName: Carol Clarke", "employeeNumber: 100003", "mail: carol@example.org",
            "memberOf: cn=Direct Members,ou=groups," + BASE_DN,
                "userPassword: carolpw");
        ds.add("dn: ou=groups," + BASE_DN, "objectClass: top", "objectClass: organizationalUnit", "ou: groups");
        ds.add("dn: cn=Hermes Users,ou=groups," + BASE_DN,
            "objectClass: top", "objectClass: groupOfNames", "cn: Hermes Users");
        ds.add("dn: cn=Direct Members,ou=groups," + BASE_DN,
            "objectClass: top", "objectClass: groupOfNames", "cn: Direct Members",
            "member: uid=carol,ou=people," + BASE_DN);
    }
    private LdapProperties buildProps(int port) {
        LdapProperties props = new LdapProperties();
        props.setEnabled(true);
        props.setProviderUrl("ldap://localhost:" + port);
        props.setBaseDn(BASE_DN);
        props.setBindDn(ADMIN_DN);
        props.setBindPassword(ADMIN_PW);
        props.setTls(false);
        props.setPageSize(100);
        props.setUserSearchFilter("(objectclass=inetOrgPerson)");
        // 标准 schema 属性名（覆盖 HSBC AD 默认值）
        LdapProperties.Attributes a = props.getAttributes();
        a.setEmployeeId("employeeNumber");
        a.setUid("uid");
        a.setSamAccountName("uid");
        a.setCn("cn");
        return props;
    }
    @Test
    @DisplayName("findUserDn 命中 uid，未知用户返回 empty")
    void findUserDn() throws Exception {
        Optional<String> dn = ldapClient.findUserDn("alice");
        assertTrue(dn.isPresent());
        assertTrue(dn.get().toLowerCase().contains("uid=alice"));
        assertTrue(ldapClient.findUserDn("nobody").isEmpty());
    }
    @Test
    @DisplayName("bindAuthenticate：正确口令 true，错误口令 false")
    void bindAuthenticate() throws Exception {
        String dn = ldapClient.findUserDn("alice").orElseThrow();
        assertTrue(ldapClient.bindAuthenticate(dn, "alicepw"));
        assertFalse(ldapClient.bindAuthenticate(dn, "wrong-password"));
    }
    @Test
    @DisplayName("fetchAllUsers 分页拉取全部 3 个用户")
    void fetchAllUsers() throws Exception {
        List<Map<String, String>> users = ldapClient.fetchAllUsers();
        assertEquals(3, users.size());
    }
    @Test
    @DisplayName("getUserAttributes 返回该 DN 的 employeeNumber/mail")
    void getUserAttributes() throws Exception {
        String dn = ldapClient.findUserDn("alice").orElseThrow();
        Map<String, String> attrs = ldapClient.getUserAttributes(dn).orElseThrow();
        assertEquals("100001", attrs.get("employeeNumber"));
        assertEquals("alice@example.org", attrs.get("mail"));
    }
    @Test
    @DisplayName("fetchUsersInGroup 优先读取 group member 属性")
    void fetchUsersInGroupViaMemberAttribute() throws Exception {
        List<Map<String, String>> users = ldapClient.fetchUsersInGroup("Direct Members");
        assertEquals(1, users.size());
        assertEquals("100003", users.get(0).get("employeeNumber"));
        assertEquals("Direct Members", users.get(0).get("_hitGroupCn"));
    }
    @Test
    @DisplayName("fetchUsersInGroup 在 group member 为空时回退 memberOf 反向搜索")
    void fetchUsersInGroupFallsBackToMemberOfSearch() throws Exception {
        List<Map<String, String>> users = ldapClient.fetchUsersInGroup("Hermes Users");
        assertEquals(1, users.size());
        assertEquals("100002", users.get(0).get("employeeNumber"));
        assertEquals("Hermes Users", users.get(0).get("_hitGroupCn"));
    }
    @Test
    @DisplayName("escapeFilterValue 转义注入字符")
    void escapeFilterValue() {
        assertEquals("a\\2ab", LdapClient.escapeFilterValue("a*b"));
        assertEquals("\\28x\\29", LdapClient.escapeFilterValue("(x)"));
    }
}