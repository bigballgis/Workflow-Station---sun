package com.admin.ldap;

import com.admin.repository.UserRepository;
import com.platform.security.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import java.util.Optional;

/**
 * LDAP 账号口令认证器：findUserDn → bind → JIT upsert。
 *
 * <p>仅 {@code ldap.enabled=true} 时创建。返回 {@link LdapAuthResult} 让上层登录逻辑决定
 * 是「认证通过」「口令错误（拒绝）」还是「LDAP 不可用/用户不在 LDAP（回退本地）」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = "enabled", havingValue = "true")
public class LdapAuthenticator {

    private final LdapClient ldapClient;
    private final LdapJitService ldapJitService;
    private final UserRepository userRepository;

    /**
     * 用 LDAP 校验账号口令。
     *
     * @return 认证结果：{@link LdapAuthOutcome#AUTHENTICATED} 携带 userId；其余为失败/回退语义
     */
    public LdapAuthResult authenticate(String username, String password) {
        final String userDn;
        try {
            Optional<String> dn = ldapClient.findUserDn(username);
            if (dn.isEmpty()) {
                // 用户不在 LDAP：交由本地登录路径处理（可能是本地管理员）
                return LdapAuthResult.of(LdapAuthOutcome.NOT_IN_LDAP, null);
            }
            userDn = dn.get();
        } catch (NamingException e) {
            log.warn("LDAP unavailable during DN lookup, will fallback to local: {}", e.getMessage());
            return LdapAuthResult.of(LdapAuthOutcome.UNAVAILABLE, null);
        }

        try {
            if (!ldapClient.bindAuthenticate(userDn, password)) {
                // 用户存在于 LDAP 但口令错误：确定性失败，不回退本地
                return LdapAuthResult.of(LdapAuthOutcome.BAD_CREDENTIALS, null);
            }
        } catch (NamingException e) {
            log.warn("LDAP unavailable during bind, will fallback to local: {}", e.getMessage());
            return LdapAuthResult.of(LdapAuthOutcome.UNAVAILABLE, null);
        }

        return resolveAfterBind(userDn, username);
    }

    /** bind 成功后做 JIT；JIT 失败则尝试用本地已有用户兜底。 */
    private LdapAuthResult resolveAfterBind(String userDn, String username) {
        try {
            Optional<String> userId = ldapJitService.jitUpsert(userDn);
            if (userId.isPresent()) {
                return LdapAuthResult.of(LdapAuthOutcome.AUTHENTICATED, userId.get());
            }
        } catch (NamingException e) {
            log.warn("LDAP JIT upsert failed after successful bind: {}", e.getMessage());
        }
        // bind 已成功，但 JIT 落库失败：用本地用户名兜底，避免误拒已认证用户
        Optional<User> local = userRepository.findByUsername(username);
        return local
                .map(u -> LdapAuthResult.of(LdapAuthOutcome.AUTHENTICATED, u.getId()))
                .orElseGet(() -> LdapAuthResult.of(LdapAuthOutcome.BAD_CREDENTIALS, null));
    }

    /** 认证结果语义。 */
    public enum LdapAuthOutcome {
        /** 认证通过（携带 userId）。 */
        AUTHENTICATED,
        /** 用户存在于 LDAP 但口令错误（拒绝，不回退）。 */
        BAD_CREDENTIALS,
        /** 用户不在 LDAP（回退本地登录）。 */
        NOT_IN_LDAP,
        /** LDAP 连接不可用（回退本地登录）。 */
        UNAVAILABLE
    }

    /** 认证结果载体。 */
    public record LdapAuthResult(LdapAuthOutcome outcome, String userId) {
        public static LdapAuthResult of(LdapAuthOutcome outcome, String userId) {
            return new LdapAuthResult(outcome, userId);
        }

        public boolean isAuthenticated() {
            return outcome == LdapAuthOutcome.AUTHENTICATED;
        }

        public boolean shouldFallbackToLocal() {
            return outcome == LdapAuthOutcome.NOT_IN_LDAP || outcome == LdapAuthOutcome.UNAVAILABLE;
        }
    }
}
