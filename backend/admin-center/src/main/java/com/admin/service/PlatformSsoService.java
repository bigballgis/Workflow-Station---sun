package com.admin.service;

import com.admin.config.PlatformSsoProperties;
import com.admin.dto.sso.SsoLoginRequest;
import com.admin.dto.sso.SsoLoginResponse;
import com.admin.dto.sso.SsoRedeemRequest;
import com.admin.dto.sso.SsoRedeemResponse;
import com.admin.ldap.LdapAuthenticator;
import com.admin.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * SSO：在统一 /login 页校验账号后签发短期 code；各子系统凭内部密钥 redeem。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSsoService {

    private static final String REDIS_KEY_PREFIX = "platform:sso:code:";

    private final PlatformSsoProperties ssoProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    /** LDAP 认证器仅 {@code ldap.enabled=true} 时存在，故用 ObjectProvider 可选注入。 */
    private final ObjectProvider<LdapAuthenticator> ldapAuthenticatorProvider;

    public SsoLoginResponse loginAndIssueCode(SsoLoginRequest request) {
        validateRedirectUri(request.getClientId(), request.getRedirectUri());

        // LDAP 为权威源：统一登录页同样优先走 LDAP bind（成功即 JIT 回写 sys_users），
        // 仅在 LDAP 关闭 / 用户不在 LDAP / LDAP 不可用时回退本地账号密码。
        User user = authenticate(request.getUsername(), request.getPassword());

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new IllegalArgumentException("Account is locked");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new IllegalArgumentException("Account is disabled");
        }

        return buildCode(user.getId(), request.getClientId(), request.getRedirectUri(), request.getState());
    }

    /**
     * 认证用户：LDAP 优先（权威源），失败语义决定是否回退本地。
     *
     * <ul>
     *   <li>AUTHENTICATED：LDAP bind 通过且已 JIT，按 userId 取回库内用户。</li>
     *   <li>NOT_IN_LDAP / UNAVAILABLE：回退本地账号密码（兼容本地管理员 / LDAP 故障）。</li>
     *   <li>BAD_CREDENTIALS：用户在 LDAP 但口令错误——权威拒绝，不回退。</li>
     * </ul>
     */
    private User authenticate(String username, String password) {
        LdapAuthenticator ldap = ldapAuthenticatorProvider.getIfAvailable();
        if (ldap != null) {
            LdapAuthenticator.LdapAuthResult result = ldap.authenticate(username, password);
            if (result.isAuthenticated()) {
                return userRepository.findById(result.userId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
            }
            if (!result.shouldFallbackToLocal()) {
                log.warn("SSO LDAP rejected credentials for user: {}", username);
                throw new IllegalArgumentException("Invalid username or password");
            }
            log.debug("SSO LDAP fallback to local for user: {} (outcome={})", username, result.outcome());
        }
        return authenticateLocal(username, password);
    }

    /** 本地账号密码认证（LDAP 关闭 / 不可用 / 用户不在 LDAP 时的回退路径）。 */
    private User authenticateLocal(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        return user;
    }

    /** 签发短期 SSO code 并落库 Redis（loginAndIssueCode / issueCodeForUser 共用）。 */
    private SsoLoginResponse buildCode(String userId, String clientId, String redirectUri, String state) {
        String code = UUID.randomUUID().toString();
        Payload payload = new Payload(userId, clientId, state);
        try {
            String json = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + code,
                    json,
                    Duration.ofSeconds(Math.max(60, ssoProperties.getCodeTtlSeconds())));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SSO payload", e);
        }
        return SsoLoginResponse.builder()
                .authorizationCode(code)
                .state(state)
                .redirectUri(redirectUri)
                .build();
    }

    /**
     * 为已通过外部机制（如 DSP 免密）确认身份的用户签发一次性 SSO code。
     *
     * <p>与 {@link #loginAndIssueCode} 共用 redirect 校验与 code 落库逻辑，区别仅在于身份已确定、
     * 不再做账号口令校验。调用方须自行保证 userId 合法且状态可登录。</p>
     *
     * @param userId      已解析的用户 id
     * @param clientId    SSO client
     * @param redirectUri 回调地址（须命中允许前缀）
     * @param state       透传 state
     */
    public SsoLoginResponse issueCodeForUser(String userId, String clientId, String redirectUri, String state) {
        validateRedirectUri(clientId, redirectUri);
        return buildCode(userId, clientId, redirectUri, state);
    }

    public SsoRedeemResponse redeem(SsoRedeemRequest request) {
        String key = REDIS_KEY_PREFIX + request.getCode();
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Invalid or expired code");
        }
        Boolean deleted = stringRedisTemplate.delete(key);
        if (!Boolean.TRUE.equals(deleted)) {
            throw new IllegalArgumentException("Invalid or expired code");
        }
        Payload payload;
        try {
            payload = objectMapper.readValue(json, Payload.class);
        } catch (JsonProcessingException e) {
            log.warn("Corrupt SSO payload in redis");
            throw new IllegalArgumentException("Invalid code");
        }
        if (!request.getClientId().equals(payload.clientId)) {
            throw new IllegalArgumentException("Client mismatch");
        }
        User user = userRepository.findById(payload.userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return SsoRedeemResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    private void validateRedirectUri(String clientId, String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri required");
        }
        if (redirectUri.contains("\r") || redirectUri.contains("\n")) {
            throw new IllegalArgumentException("Invalid redirectUri");
        }
        PlatformSsoProperties.SsoClient client = ssoProperties.getClients().get(clientId);
        if (client == null || !client.isEnabled()) {
            throw new IllegalArgumentException("Unknown or disabled client");
        }
        List<String> prefixes = client.getRedirectUriPrefixes();
        if (prefixes == null || prefixes.isEmpty()) {
            throw new IllegalStateException("SSO client not configured: " + clientId);
        }
        boolean ok = prefixes.stream().anyMatch(redirectUri::startsWith);
        if (!ok) {
            log.warn("SSO redirect rejected for client {} uri {} (allowedPrefixes={})", clientId, redirectUri, prefixes);
            throw new IllegalArgumentException("redirectUri not allowed");
        }
    }

    private record Payload(String userId, String clientId, String state) {
    }
}
