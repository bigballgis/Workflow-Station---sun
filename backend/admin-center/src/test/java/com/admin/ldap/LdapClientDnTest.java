package com.admin.ldap;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.naming.NamingException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DNs that a plain JNDI {@code String} name breaks: a '/' in an RDN (JNDI reads the String as a composite name and
 * cuts it at the '/'), an escaped comma, and a multi-valued RDN. Every read and search by DN in {@link LdapClient}
 * must reach the entry the DN names. Runs against an UnboundID in-memory directory.
 */
class LdapClientDnTest {

    private static final String BASE_DN = "dc=example,dc=org";
    private static final String PEOPLE = "ou=people," + BASE_DN;
    private static final String GROUPS = "ou=groups," + BASE_DN;
    private static final String APAC = "ou=Asia/Pacific," + BASE_DN;
    private static final String SLASH_USER = "cn=Ops/Dana," + PEOPLE;
    private static final String COMMA_USER = "cn=Smith\\, John," + PEOPLE;
    private static final String MULTI_VALUED_USER = "cn=Lee+uid=lee," + PEOPLE;

    private InMemoryDirectoryServer ds;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
        config.addAdditionalBindCredentials("cn=admin," + BASE_DN, "admin");
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", 0));
        config.setSchema(null);
        ds = new InMemoryDirectoryServer(config);
        ds.startListening();
        ds.add("dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: example");
        ds.add("dn: " + PEOPLE, "objectClass: top", "objectClass: organizationalUnit", "ou: people");
        ds.add("dn: " + GROUPS, "objectClass: top", "objectClass: organizationalUnit", "ou: groups");
        ds.add("dn: " + APAC, "objectClass: top", "objectClass: organizationalUnit", "ou: Asia/Pacific");
        person(SLASH_USER, "dana", "200001", "cn: Ops/Dana");
        person(COMMA_USER, "jsmith", "200002", "cn: Smith, John");
        person(MULTI_VALUED_USER, "lee", "200003", "cn: Lee");
        person("cn=Mgr/One," + PEOPLE, "mgr1", "200009", "cn: Mgr/One");
        person("uid=pat," + APAC, "pat", "200010", "cn: Pat");
        ds.add("dn: cn=HK/Ops Approvers," + GROUPS,
                "objectClass: top", "objectClass: groupOfNames", "cn: HK/Ops Approvers",
                "member: " + SLASH_USER, "member: " + COMMA_USER, "member: " + MULTI_VALUED_USER);
        ds.add("dn: cn=APAC Team," + APAC,
                "objectClass: top", "objectClass: groupOfNames", "cn: APAC Team",
                "member: uid=pat," + APAC);
    }

    @AfterEach
    void tearDown() {
        ds.shutDown(true);
    }

    @Test
    void shouldReadAUser_whoseDnHasASlash() throws Exception {
        Map<String, String> attrs = client(BASE_DN).getUserAttributes(SLASH_USER).orElseThrow();

        assertThat(attrs.get("employeeNumber")).isEqualTo("200001");
        assertThat(attrs.get("cn")).isEqualTo("Ops/Dana");
    }

    @Test
    void shouldReadAUser_whoseDnHasAnEscapedCommaOrAMultiValuedRdn() throws Exception {
        LdapClient client = client(BASE_DN);

        assertThat(client.getUserAttributes(COMMA_USER).orElseThrow().get("employeeNumber")).isEqualTo("200002");
        assertThat(client.getUserAttributes(MULTI_VALUED_USER).orElseThrow().get("employeeNumber"))
                .isEqualTo("200003");
    }

    @Test
    void shouldReadEveryMemberOfAGroup_whoseNameAndMembersHaveSpecialCharacters() throws Exception {
        LdapClient.AdGroupMembers members = client(BASE_DN).fetchUsersInGroup("HK/Ops Approvers");

        assertThat(members.complete()).isTrue();
        assertThat(members.users()).extracting(user -> user.get("employeeNumber"))
                .containsExactlyInAnyOrder("200001", "200002", "200003");
    }

    @Test
    void shouldFindAManagerByEmployeeId_whoseDnHasASlash() throws Exception {
        Map<String, String> attrs = client(BASE_DN).getUserAttributesByEmployeeId("200009").orElseThrow();

        assertThat(attrs.get("uid")).isEqualTo("mgr1");
    }

    @Test
    void shouldSearchUnderABaseDn_thatHasASlash() throws Exception {
        LdapClient client = client(APAC);

        assertThat(client.findUserDn("pat")).hasValueSatisfying(dn -> assertThat(dn).startsWithIgnoringCase("uid=pat,"));
        assertThat(client.fetchAllUsers()).extracting(user -> user.get("uid")).containsExactly("pat");
        LdapClient.AdGroupMembers apac = client.fetchUsersInGroup("APAC Team");
        assertThat(apac.complete()).isTrue();
        assertThat(apac.users()).extracting(user -> user.get("uid")).containsExactly("pat");
    }

    @Test
    void shouldReportAMalformedDn_asAReadFailure() throws Exception {
        LdapClient client = client(BASE_DN);
        ds.add("dn: cn=Odd Member," + GROUPS, "objectClass: top", "objectClass: groupOfNames", "cn: Odd Member",
                "member: " + SLASH_USER, "member: not a distinguished name");

        assertThatThrownBy(() -> client.getUserAttributes("not a distinguished name"))
                .isInstanceOf(NamingException.class);
        LdapClient.AdGroupMembers members = client.fetchUsersInGroup("Odd Member");
        assertThat(members.complete()).isFalse();
        assertThat(members.users()).extracting(user -> user.get("employeeNumber")).containsExactly("200001");
    }

    private void person(String dn, String uid, String employeeNumber, String cn) throws Exception {
        ds.add("dn: " + dn, "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson", cn, "uid: " + uid, "sn: " + uid, "employeeNumber: " + employeeNumber);
    }

    private LdapClient client(String baseDn) {
        LdapProperties props = new LdapProperties();
        props.setEnabled(true);
        props.setProviderUrl("ldap://localhost:" + ds.getListenPort());
        props.setBaseDn(baseDn);
        props.setBindDn("cn=admin," + BASE_DN);
        props.setBindPassword("admin");
        props.setTls(false);
        props.setPageSize(100);
        props.setUserSearchFilter("(objectclass=inetOrgPerson)");
        props.getGroupSync().setSearchBaseDn(baseDn);
        LdapProperties.Attributes names = props.getAttributes();
        names.setEmployeeId("employeeNumber");
        names.setUid("uid");
        names.setSamAccountName("uid");
        names.setCn("cn");
        return new LdapClient(props);
    }
}
