package com.admin.servicetask.client;

import com.admin.servicetask.CurrentActor;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.admin.exception.ServiceTaskApiException;
import com.platform.common.dto.UserPrincipal;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import com.admin.config.RestTemplateConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Activepieces REST API 客户端。
 *
 * <p><b>身份模型：谁进去就是谁。</b>AP 侧不再有共享账号——每一次会话都按<b>真实操作人</b>
 * 经 managed-authn 外部 token（RS256）换取，AP 里的每个动作因此天然归属发起人。
 * 签名私钥仅服务端持有，浏览器只拿到换来的 AP token。
 */
@Slf4j
@Component
public class ServiceTaskApiClient {

    /** AP control-plane calls only — long read timeout, own breaker (see RestTemplateConfig). */
    private final RestTemplate restTemplate;
    private final ServiceTaskProperties properties;

    public ServiceTaskApiClient(@Qualifier(RestTemplateConfig.AP_REST_TEMPLATE) RestTemplate restTemplate,
                                ServiceTaskProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * 一次 AP 会话。AP 的一个完整前端会话需要 token + projectId
     * （AP 的 clearSession 同时清这两个 key），故桥页必须两者都写入 localStorage。
     */
    public record ApSession(String token, String projectId, String platformId) {}

    /**
     * 以<b>当前操作人</b>换取 AP 会话——服务层写路径（flow/piece 的导入、启停、删除）的唯一入口。
     *
     * <p>操作人从 {@code SecurityContext} 取（UI 的平台 JWT，或 C-3 服务令牌 + {@code X-User-Id}），
     * 取不到即 {@link com.admin.exception.ServiceTaskActorRequiredException} fail-loud，
     * 不回退任何共享身份（见 {@link CurrentActor}）。
     */
    public ApSession signInAsCurrentActor() {
        return signInManaged(CurrentActor.require());
    }

    /**
     * Per-user provisioning（审计到人）。为当前 DW 用户签发 AP 外部 token（RS256，
     * header {@code kid} = 配置的 signing key id），POST 到 AP
     * {@code /api/v1/managed-authn/external-token}，换取<b>该用户专属</b>的 AP 会话。
     *
     * <p>AP 侧据 {@code externalUserId=DW userId} getOrCreate 一个影子 AP 用户并绑到共享
     * project——AP 的每一步操作因此天然映射回发起的 DW 人（审计到人）。签名私钥仅服务端持有，
     * 浏览器只拿到换来的 AP token。
     *
     * <p>这是<b>唯一</b>的 AP 身份来源（共享账号已移除）。未配置签名密钥时在此 fail-loud，
     * 而不是悄悄换一个别的身份继续。
     *
     * @param user 已认证的当前 DW 用户
     * @return {@link ApSession}（该用户专属 token + 共享 projectId）
     * @throws ServiceTaskApiException 未配置签名密钥、签名失败、AP 换取失败或超时
     */
    public ApSession signInManaged(UserPrincipal user) {
        ServiceTaskProperties.Managed managed = properties.getManaged();
        if (managed.getSigningKeyId() == null || managed.getSigningKeyId().isBlank()
                || managed.getPrivateKey() == null || managed.getPrivateKey().isBlank()) {
            throw new ServiceTaskApiException("Activepieces managed provisioning not configured (signing key id / private key)");
        }

        String externalToken = buildExternalToken(user);

        String base = properties.getInternalUrl();
        String url = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + "/api/v1/managed-authn/external-token";

        Map<String, Object> body = new HashMap<>();
        body.put("externalAccessToken", externalToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.debug("Activepieces managed external-token exchange: {}", url);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object token = response.getBody().get("token");
                Object projectId = response.getBody().get("projectId");
                Object platformId = response.getBody().get("platformId");
                if (token != null && !token.toString().isBlank()) {
                    log.debug("Activepieces managed exchange successful (externalUserId={}, projectId={})",
                            user.getUserId(), projectId);
                    return new ApSession(token.toString(),
                            projectId != null ? projectId.toString() : null,
                            platformId != null ? platformId.toString() : null);
                }
            }
            throw new ServiceTaskApiException("Activepieces managed exchange failed: no token in response");

        } catch (ResourceAccessException e) {
            log.error("Activepieces managed exchange timeout or connection error: {}", e.getMessage(), e);
            throw new ServiceTaskApiException("Activepieces API timeout or connection error", e);
        } catch (RestClientException e) {
            log.error("Activepieces managed exchange failed: {}", e.getMessage(), e);
            throw new ServiceTaskApiException("Activepieces API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * 按 AP external-token payload 契约签发 RS256 外部 token。<b>六个 claim 全部必填</b>
     * （{@code externalUserId / externalProjectId / firstName / lastName / role / platformRole}）——
     * AP 侧的 schema 是为本方法量身定的、没有可选分支也没有版本变体，
     * 因为这条链上永远只有一个签发方（见 DECISIONS.md D13 裁决 2）。
     * {@code kid} 走 header，AP 据此查 publicKey 验签。
     */
    private String buildExternalToken(UserPrincipal user) {
        ServiceTaskProperties.Managed managed = properties.getManaged();
        // 空配置在这里就断掉，而不是发一个缺 claim 的 token 让 AP 去猜默认值。
        String projectRole = requireConfigured(managed.getProjectRole(), "service-task.managed.project-role");
        String platformRole = requireConfigured(managed.getPlatformRole(), "service-task.managed.platform-role");
        String firstName = firstNonBlank(user.getDisplayName(), user.getUsername(), user.getUserId());
        String email = user.getEmail();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + managed.getTokenTtlSeconds() * 1000L);
        try {
            JwtBuilder builder = Jwts.builder()
                    .header().keyId(managed.getSigningKeyId()).and()
                    .claim("externalUserId", user.getUserId())
                    .claim("externalProjectId", managed.getProjectExternalId())
                    .claim("firstName", firstName)
                    .claim("lastName", "")
                    // role = project_role.name（Admin/Editor/Viewer）；platformRole = AP 平台角色
                    // （ADMIN/MEMBER）。AP 每次握手按这两个值同步既有影子用户。
                    .claim("role", projectRole)
                    .claim("platformRole", platformRole);
            // email 是可选 claim：LDAP 账号可能没有 mail 属性。带上时 AP 用真实邮箱建/升级
            // 影子 identity（否则回退 sha256 哈希邮箱——Automation Studio 界面会露出来）。
            if (email != null && !email.isBlank()) {
                builder.claim("email", email);
            }
            return builder
                    .issuedAt(now)
                    .expiration(expiry)
                    .signWith(loadPrivateKey(managed.getPrivateKey()), Jwts.SIG.RS256)
                    .compact();
        } catch (ServiceTaskApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to sign Activepieces external token: {}", e.getMessage(), e);
            throw new ServiceTaskApiException("Failed to sign Activepieces external token", e);
        }
    }

    /** 解析 PKCS8 PEM 私钥（AP signing-key generator 产 PKCS8）。解析后缓存，避免每次握手重复解析。 */
    private PrivateKey loadPrivateKey(String pem) {
        PrivateKey cached = cachedPrivateKey;
        if (cached != null) {
            return cached;
        }
        try {
            String der = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] bytes = Base64.getDecoder().decode(der);
            PrivateKey key = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(bytes));
            cachedPrivateKey = key;
            return key;
        } catch (RuntimeException | java.security.GeneralSecurityException e) {
            throw new ServiceTaskApiException("Invalid Activepieces managed private key (expected PKCS8 PEM)", e);
        }
    }

    private static String requireConfigured(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new ServiceTaskApiException("Activepieces managed provisioning misconfigured: " + property + " must be set");
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "user";
    }

    private volatile PrivateKey cachedPrivateKey;
}
