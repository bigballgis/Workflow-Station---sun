package com.admin.ldap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import javax.naming.NamingException;
import java.util.Map;
import java.util.Optional;
/**
 * 登录时 JIT（Just-In-Time）拉取并 upsert 单个 LDAP 用户，确保以 AD 为权威源实时回写画像。
 *
 * <p>仅 {@code ldap.enabled=true} 时创建。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ldap", name = "enabled", havingValue = "true")
public class LdapJitService {
    private final LdapClient ldapClient;
    private final LdapUserMapper ldapUserMapper;
    private final LdapUserSyncService ldapUserSyncService;
    /**
     * 按 DN 取最新属性、映射并 upsert。
     *
      * @return 落库用户的真实 userId；属性缺失无法映射时返回 empty
     * @throws NamingException LDAP 读取异常
     */
    public Optional<String> jitUpsert(String userDn) throws NamingException {
        Optional<Map<String, String>> attrs = ldapClient.getUserAttributes(userDn);
        if (attrs.isEmpty()) {
            return Optional.empty();
        }
        Optional<LdapUserData> mapped = ldapUserMapper.mapToUser(attrs.get());
        if (mapped.isEmpty()) {
            log.warn("LDAP JIT skipped: mapped user has no employeeID");
            return Optional.empty();
        }
        String persistedUserId = ldapUserSyncService.upsertUserReturningId(mapped.get());
        return Optional.ofNullable(persistedUserId);
    }
}