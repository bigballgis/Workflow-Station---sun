package com.admin.servicetask.client;

import com.admin.servicetask.config.ServiceTaskProperties;
import com.admin.exception.ServiceTaskApiException;
import com.platform.common.dto.UserPrincipal;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
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
 * <p>仅封装「共享账号服务端 sign-in」：用平台持有的共享服务账号（ADMIN）登录 AP，换取 AP token。
 * 浏览器永不接触共享账号口令——登录桥只拿到换来的 token 写入 localStorage。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceTaskApiClient {

    private final RestTemplate restTemplate;
    private final ServiceTaskProperties properties;

    /**
     * 共享账号 sign-in 的结果。AP 的一个完整前端会话需要 token + projectId
     * （AP 的 clearSession 同时清这两个 key），故桥页必须两者都写入 localStorage。
     */
    public record ApSession(String token, String projectId) {}

    /**
     * 调用 AP {@code POST /api/v1/authentication/sign-in}，用共享账号换取 AP 会话。
     *
     * @return {@link ApSession}（token 写 localStorage['token']，projectId 写 localStorage['projectId']）
     * @throws ServiceTaskApiException 未配置共享账号、登录失败或超时
     */
    public ApSession signInShared() {
        String email = properties.getSharedAccount().getEmail();
        String password = properties.getSharedAccount().getPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new ServiceTaskApiException("Activepieces shared account not configured");
        }

        String base = properties.getInternalUrl();
        String url = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + "/api/v1/authentication/sign-in";

        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            log.debug("Activepieces shared sign-in: {}", url);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object token = response.getBody().get("token");
                Object projectId = response.getBody().get("projectId");
                if (token != null && !token.toString().isBlank()) {
                    log.debug("Activepieces shared sign-in successful (projectId={})", projectId);
                    return new ApSession(token.toString(), projectId != null ? projectId.toString() : null);
                }
            }
            throw new ServiceTaskApiException("Activepieces sign-in failed: no token in response");

        } catch (ResourceAccessException e) {
            log.error("Activepieces sign-in timeout or connection error: {}", e.getMessage(), e);
            throw new ServiceTaskApiException("Activepieces API timeout or connection error", e);
        } catch (RestClientException e) {
            log.error("Activepieces sign-in failed: {}", e.getMessage(), e);
            throw new ServiceTaskApiException("Activepieces API call failed: " + e.getMessage(), e);
        }
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
                if (token != null && !token.toString().isBlank()) {
                    log.debug("Activepieces managed exchange successful (externalUserId={}, projectId={})",
                            user.getUserId(), projectId);
                    return new ApSession(token.toString(), projectId != null ? projectId.toString() : null);
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
     * 按 AP external-token-extractor 的 v2 payload 契约签发 RS256 外部 token：
     * {@code externalUserId / externalProjectId / firstName / lastName}（均必填）。
     * 不带 {@code role} → AP 默认 EDITOR。{@code kid} 走 header，AP 据此查 publicKey 验签。
     */
    private String buildExternalToken(UserPrincipal user) {
        ServiceTaskProperties.Managed managed = properties.getManaged();
        String firstName = firstNonBlank(user.getDisplayName(), user.getUsername(), user.getUserId());
        Date now = new Date();
        Date expiry = new Date(now.getTime() + managed.getTokenTtlSeconds() * 1000L);
        try {
            return Jwts.builder()
                    .header().keyId(managed.getSigningKeyId()).and()
                    .claim("externalUserId", user.getUserId())
                    .claim("externalProjectId", managed.getProjectExternalId())
                    .claim("firstName", firstName)
                    .claim("lastName", "")
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
