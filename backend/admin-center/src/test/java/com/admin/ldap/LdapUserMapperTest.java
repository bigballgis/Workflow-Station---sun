package com.admin.ldap;

import com.platform.security.model.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LdapUserMapper} 单元测试：覆盖字段映射表的关键规则——
 * employeeID 主键、username 多来源优先级、displayName 拼装、phone 归一化、lockoutTime→status、缺主键跳过。
 */
@DisplayName("LdapUserMapper 字段映射")
class LdapUserMapperTest {

    /** 用默认 HSBC AD 属性名构造 mapper（测试数据按这些键名提供）。 */
    private LdapUserMapper newMapper() {
        LdapProperties props = new LdapProperties();
        return new LdapUserMapper(props);
    }

    private Map<String, String> baseAttrs() {
        Map<String, String> m = new HashMap<>();
        m.put("employeeID", "100001");
        m.put("uid", "alice");
        m.put("displayName", "Alice Anderson");
        m.put("mail", "alice@example.org");
        m.put("telephoneNumber", "+852 1234-0001");
        m.put("title", "Engineering Manager");
        m.put("hsbc-ad-LineManagerID", "44027893");
        m.put("hsbc-ad-AuthManagerEmpId", "44026666");
        return m;
    }
    @Test
    @DisplayName("完整属性 → 全字段映射，phone 去空格/横线，manager 映射到 em/fm")
    void mapsFullAttributes() {
        Optional<LdapUserData> result = newMapper().mapToUser(baseAttrs());
        assertTrue(result.isPresent());
        LdapUserData u = result.get();
        assertEquals("100001", u.getId());
        assertEquals("100001", u.getEmployeeId());
        assertEquals("alice", u.getUsername());
        assertEquals("Alice Anderson", u.getDisplayName());
        assertEquals("Alice Anderson", u.getFullName());
        assertEquals("alice@example.org", u.getEmail());
        assertEquals("+85212340001", u.getPhone());
        assertEquals("Engineering Manager", u.getPosition());
        assertEquals("44027893", u.getEntityManagerId());
        assertEquals("44026666", u.getFunctionManagerId());
        assertEquals(UserStatus.ACTIVE, u.getStatus());
    }
    @Test
    @DisplayName("line manager id 缺失时回退到 managerEmpId")
    void managerFallsBackToIdAttributes() {
        Map<String, String> attrs = baseAttrs();
        attrs.remove("hsbc-ad-LineManagerID");
        attrs.put("hsbc-ad-managerEmpId", "LM100");
        attrs.put("hsbc-ad-AuthManagerEmpId", "AM200");
        LdapUserData u = newMapper().mapToUser(attrs).orElseThrow();
        assertEquals("LM100", u.getEntityManagerId());
        assertEquals("AM200", u.getFunctionManagerId());
    }
    @Test
    @DisplayName("title/workRole 缺失时回退 postalAddress 作为岗位")
    void positionFallsBackToPostalAddress() {
        Map<String, String> attrs = baseAttrs();
        attrs.remove("title");
        attrs.put("postalAddress", "SW Engineer");
        LdapUserData u = newMapper().mapToUser(attrs).orElseThrow();
        assertEquals("SW Engineer", u.getPosition());
    }
    @Test
    @DisplayName("缺 employeeID → 跳过该条（Optional.empty）")
    void skipsWhenNoEmployeeId() {
        Map<String, String> attrs = baseAttrs();
        attrs.remove("employeeID");
        assertTrue(newMapper().mapToUser(attrs).isEmpty());
    }
    @Test
    @DisplayName("username 优先级：uid 缺失时回退 samAccountName")
    void usernameFallsBackToSamAccountName() {
        Map<String, String> attrs = baseAttrs();
        attrs.remove("uid");
        attrs.put("hsbc-ad-SAMAccountName", "alice.sam");
        assertEquals("alice.sam", newMapper().mapToUser(attrs).orElseThrow().getUsername());
    }
    @Test
    @DisplayName("username 全部来源缺失时回退 employeeID")
    void usernameFallsBackToEmployeeId() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("employeeID", "100009");
        assertEquals("100009", newMapper().mapToUser(attrs).orElseThrow().getUsername());
    }
    @Test
    @DisplayName("displayName 缺失 → givenName + sn 拼装")
    void displayNameComposedFromGivenAndSurname() {
        Map<String, String> attrs = baseAttrs();
        attrs.remove("displayName");
        attrs.put("givenName", "Alice");
        attrs.put("sn", "Anderson");
        assertEquals("Alice Anderson", newMapper().mapToUser(attrs).orElseThrow().getDisplayName());
    }
    @Test
    @DisplayName("displayName 为账号技术值时回退 cn")
    void displayNameFallsBackWhenTechnicalValue() {
        Map<String, String> attrs = baseAttrs();
        attrs.put("displayName", "alice");
        attrs.put("cn", "Alice Cn");
        assertEquals("Alice Cn", newMapper().mapToUser(attrs).orElseThrow().getDisplayName());
    }

    @Test
    @DisplayName("lockoutTime 非 0 → LOCKED；为 0 → ACTIVE")
    void statusDerivedFromLockoutTime() {
        Map<String, String> locked = baseAttrs();
        locked.put("lockoutTime", "133000000000000000");
        assertEquals(UserStatus.LOCKED, newMapper().mapToUser(locked).orElseThrow().getStatus());

        Map<String, String> active = baseAttrs();
        active.put("lockoutTime", "0");
        assertEquals(UserStatus.ACTIVE, newMapper().mapToUser(active).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("phone 仅备用号(IntlTelNumber)时取备用并归一化；都空则 null")
    void phoneFallbackAndNull() {
        Map<String, String> fallback = baseAttrs();
        fallback.remove("telephoneNumber");
        fallback.put("hsbc-ad-IntlTelNumber", "(852) 9999 8888");
        assertEquals("85299998888", newMapper().mapToUser(fallback).orElseThrow().getPhone());

        Map<String, String> none = baseAttrs();
        none.remove("telephoneNumber");
        assertNull(newMapper().mapToUser(none).orElseThrow().getPhone());
    }
}
