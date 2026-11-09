package com.admin.ldap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LdapClient#readMemberRanges}: AD answers a large group in ranges and names the range that holds
 * the last member {@code member;range=<start>-*}; every member must be read, and a failure part way must
 * not pass for the whole group.
 */
class LdapClientMemberRangeTest {

    @Test
    @DisplayName("AD 分段返回：读到以 -* 结尾的最后一段为止，一个不少")
    void readsEveryRangeUpToTheLastOne() throws NamingException {
        List<String> requests = new ArrayList<>();
        List<String> members = LdapClient.readMemberRanges(ids -> {
            requests.add(String.join(",", ids));
            return requests.size() == 1
                    ? answer("member;range=0-1499", 0, 1500)
                    : answer("member;range=1500-*", 1500, 300);
        });

        assertThat(members).hasSize(1800).startsWith("cn=u0").endsWith("cn=u1799");
        assertThat(requests).containsExactly("member;range=0-*,member", "member;range=1500-*");
    }

    @Test
    @DisplayName("不分段的服务器返回普通 member：一次读完")
    void readsPlainMemberInOneRequest() throws NamingException {
        List<String> requests = new ArrayList<>();
        List<String> members = LdapClient.readMemberRanges(ids -> {
            requests.add(String.join(",", ids));
            return answer("member", 0, 3);
        });

        assertThat(members).containsExactly("cn=u0", "cn=u1", "cn=u2");
        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("属性名大小写不同也能识别最后一段")
    void matchesTheRangeNameWhateverItsCase() throws NamingException {
        List<String> members = LdapClient.readMemberRanges(ids -> answer("Member;Range=0-*", 0, 2));

        assertThat(members).hasSize(2);
    }

    @Test
    @DisplayName("第一段之后读失败：抛出，不把截断的名单当成全部")
    void aFailureAfterTheFirstRangeIsThrown() {
        int[] calls = {0};
        assertThatThrownBy(() -> LdapClient.readMemberRanges(ids -> {
            if (calls[0]++ == 0) {
                return answer("member;range=0-1499", 0, 1500);
            }
            throw new NamingException("connection reset");
        })).isInstanceOf(NamingException.class);
    }

    @Test
    @DisplayName("服务器不认分段选项：退回读普通 member")
    void fallsBackToPlainMemberWhenRangesAreRejected() throws NamingException {
        int[] calls = {0};
        List<String> members = LdapClient.readMemberRanges(ids -> {
            if (calls[0]++ == 0) {
                throw new NamingException("unsupported option");
            }
            return answer("member", 0, 2);
        });

        assertThat(members).hasSize(2);
    }

    @Test
    @DisplayName("组没有 member：返回空")
    void aGroupWithoutMembersIsEmpty() throws NamingException {
        assertThat(LdapClient.readMemberRanges(ids -> new BasicAttributes(true))).isEmpty();
    }

    private static Attributes answer(String attributeId, int from, int count) {
        BasicAttribute attr = new BasicAttribute(attributeId);
        for (int i = from; i < from + count; i++) {
            attr.add("cn=u" + i);
        }
        BasicAttributes attrs = new BasicAttributes(true);
        attrs.put(attr);
        return attrs;
    }
}
