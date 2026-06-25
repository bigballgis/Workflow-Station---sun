package com.admin.sso.dsp;

import com.admin.config.PlatformSsoProperties;
import com.admin.dto.sso.SsoLoginResponse;
import com.admin.ldap.LdapClient;
import com.admin.ldap.LdapJitService;
import com.admin.repository.UserRepository;
import com.admin.service.PlatformSsoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * DSP 免密 SSO 编排：AMToken → translator 换 E2E/JWT → 解析 claims 定位用户 → 复用 SSO code 流程。
 *
 * <p>身份解析顺序：LDAP（启用时，按 employeeId/username 搜 DN 并 JIT，AD 为权威）→ 本地 {@code sys_users}。
 * LDAP Bean 仅在 {@code ldap.enabled=true} 时存在，故用 {@link ObjectProvider} 软依赖获取。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DspSsoService {

    private final PlatformSsoProperties ssoProperties;
    private final DspTokenClient dspTokenClient;
    private final DspJwtDecoder dspJwtDecoder;
    private final PlatformSsoService platformSsoService;
    private final UserRepository userRepository;
    private final ObjectProvider<LdapClient> ldapClientProvider;
    private final ObjectProvider<LdapJitService> ldapJitServiceProvider;

    /**
     * 处理免密登录，签发一次性 SSO code。
     *
     * @throws IllegalStateException    DSP 未启用
     * @throws IllegalArgumentException AMToken 缺失 / 无法定位用户 / 账号不可登录
     */
    public SsoLoginResponse passwordless(DspPasswordlessRequest request, String e2eHeaderToken) {
        PlatformSsoProperties.Dsp dsp = ssoProperties.getDsp();
        if (!dsp.isEnabled()) {
            throw new IllegalStateException("DSP passwordless sign-in is disabled");
        }

        String issuedToken = resolveIssuedToken(request, dsp, e2eHeaderToken);
        JsonNode claims = dspJwtDecoder.decodePayload(issuedToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid DSP token"));

        String employeeId = dspJwtDecoder.firstClaim(claims, dsp.getEmployeeIdClaimNames()).orElse(null);
        String username = dspJwtDecoder.firstClaim(claims, dsp.getUsernameClaimNames()).orElse(null);
        if (employeeId == null && username == null) {
            throw new IllegalArgumentException("DSP token has no resolvable identity claim");
        }

        String userId = resolveUserId(employeeId, username)
                .orElseThrow(() -> new IllegalArgumentException("No matching user for DSP identity"));
        assertLoginable(userId);

        return platformSsoService.issueCodeForUser(
                userId, request.getClientId(), request.getRedirectUri(), request.getState());
    }

    /** 取 E2E/JWT：网关已注入且允许接受 → 直接用；否则用 AMToken 调 translator。 */
    private String resolveIssuedToken(DspPasswordlessRequest request, PlatformSsoProperties.Dsp dsp,
                                      String e2eHeaderToken) {
        if (dsp.isAcceptGatewayE2eToken() && e2eHeaderToken != null && !e2eHeaderToken.isBlank()) {
            return e2eHeaderToken;
        }
        if (request.getAmToken() == null || request.getAmToken().isBlank()) {
            throw new IllegalArgumentException("Missing AMToken");
        }
        return dspTokenClient.translate(request.getAmToken());
    }

    private Optional<String> resolveUserId(String employeeId, String username) {
        Optional<String> viaLdap = resolveViaLdap(employeeId, username);
        if (viaLdap.isPresent()) {
            return viaLdap;
        }
        return resolveLocal(employeeId, username);
    }

    /** LDAP 启用时按 employeeId/username 搜 DN 并 JIT，返回落库 userId。 */
    private Optional<String> resolveViaLdap(String employeeId, String username) {
        LdapClient ldapClient = ldapClientProvider.getIfAvailable();
        LdapJitService jit = ldapJitServiceProvider.getIfAvailable();
        if (ldapClient == null || jit == null) {
            return Optional.empty();
        }
        for (String candidate : distinctNonNull(employeeId, username)) {
            try {
                Optional<String> dn = ldapClient.findUserDn(candidate);
                if (dn.isPresent()) {
                    Optional<String> userId = jit.jitUpsert(dn.get());
                    if (userId.isPresent()) {
                        return userId;
                    }
                }
            } catch (NamingException e) {
                log.warn("LDAP lookup failed during DSP resolve, will try local: {}", e.getMessage());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** 本地兜底：优先按 employeeId(=id) 查，其次按 username。 */
    private Optional<String> resolveLocal(String employeeId, String username) {
        if (employeeId != null) {
            Optional<User> byId = userRepository.findById(employeeId);
            if (byId.isPresent()) {
                return Optional.of(byId.get().getId());
            }
        }
        if (username != null) {
            return userRepository.findByUsername(username).map(User::getId);
        }
        return Optional.empty();
    }

    private void assertLoginable(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new IllegalArgumentException("Account is locked");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new IllegalArgumentException("Account is disabled");
        }
    }

    private Set<String> distinctNonNull(String... values) {
        Set<String> set = new LinkedHashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                set.add(v.trim());
            }
        }
        return set;
    }
}
