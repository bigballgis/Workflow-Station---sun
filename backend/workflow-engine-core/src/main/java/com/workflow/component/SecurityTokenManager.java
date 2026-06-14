package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 安全令牌协作类
 *
 * 从 {@link SecurityManagerComponent} 拆分而来，负责 JWT 访问/刷新令牌的生成、解析、
 * 校验、缓存与黑名单管理。纯结构搬迁，行为与原实现逐字一致。
 *
 * <p>令牌签名使用的密钥、过期时间从门面传入；签名所用的哈希委托 {@link SecurityCryptoHelper}；
 * 访问令牌中的角色声明委托门面 {@code getUserRoles}。
 */
@Slf4j
@Component
public class SecurityTokenManager {

    private static final String TOKEN_CACHE_PREFIX = "security:token:";
    private static final String BLACKLIST_PREFIX = "security:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityCryptoHelper cryptoHelper;

    public SecurityTokenManager(StringRedisTemplate stringRedisTemplate,
                                ObjectMapper objectMapper,
                                SecurityCryptoHelper cryptoHelper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.cryptoHelper = cryptoHelper;
    }

    /**
     * 生成访问令牌
     */
    public String generateAccessToken(String username, String jwtSecretKey, long jwtExpirationMs,
                                      Set<String> roles) {
        long now = System.currentTimeMillis();
        long expiration = now + jwtExpirationMs;

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", username);
        claims.put("iat", now);
        claims.put("exp", expiration);
        claims.put("type", "access");
        claims.put("roles", roles);

        try {
            String payload = objectMapper.writeValueAsString(claims);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

            // 简化的签名（实际应使用HMAC-SHA256）
            String signature = cryptoHelper.hashPassword(encodedPayload + jwtSecretKey);

            return encodedPayload + "." + signature;

        } catch (JsonProcessingException e) {
            throw new WorkflowBusinessException("TOKEN_GENERATION_FAILED", "Token generation failed");
        }
    }

    /**
     * 生成刷新令牌
     */
    public String generateRefreshToken(String username, String jwtSecretKey, long refreshTokenExpirationMs) {
        long now = System.currentTimeMillis();
        long expiration = now + refreshTokenExpirationMs;

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", username);
        claims.put("iat", now);
        claims.put("exp", expiration);
        claims.put("type", "refresh");
        claims.put("jti", UUID.randomUUID().toString());

        try {
            String payload = objectMapper.writeValueAsString(claims);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

            String signature = cryptoHelper.hashPassword(encodedPayload + jwtSecretKey);

            return encodedPayload + "." + signature;

        } catch (JsonProcessingException e) {
            throw new WorkflowBusinessException("TOKEN_GENERATION_FAILED", "Refresh token generation failed");
        }
    }

    /**
     * 解析令牌
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseToken(String token, String jwtSecretKey) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return null;
            }

            String encodedPayload = parts[0];
            String signature = parts[1];

            // 验证签名
            String expectedSignature = cryptoHelper.hashPassword(encodedPayload + jwtSecretKey);
            if (!expectedSignature.equals(signature)) {
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload),
                    StandardCharsets.UTF_8);

            return objectMapper.readValue(payload, Map.class);

        } catch (Exception e) {
            log.error("令牌解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证刷新令牌
     */
    public String validateRefreshToken(String refreshToken, String jwtSecretKey) {
        Map<String, Object> claims = parseToken(refreshToken, jwtSecretKey);

        if (claims == null) {
            return null;
        }

        // 检查令牌类型
        if (!"refresh".equals(claims.get("type"))) {
            return null;
        }

        // 检查过期时间
        long expiration = ((Number) claims.get("exp")).longValue();
        if (System.currentTimeMillis() > expiration) {
            return null;
        }

        return (String) claims.get("sub");
    }

    /**
     * 缓存令牌
     */
    public void cacheToken(String username, String accessToken, String refreshToken) {
        String tokenKey = TOKEN_CACHE_PREFIX + username;
        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);
        tokens.put("createdAt", LocalDateTime.now().toString());

        stringRedisTemplate.opsForHash().putAll(tokenKey, tokens);
        stringRedisTemplate.expire(tokenKey, Duration.ofDays(7));
    }

    /**
     * 将令牌加入黑名单
     */
    public void blacklistToken(String token) {
        String blacklistKey = BLACKLIST_PREFIX + cryptoHelper.hashPassword(token).substring(0, 32);
        stringRedisTemplate.opsForValue().set(blacklistKey, "1", Duration.ofDays(7));
    }

    /**
     * 检查令牌是否在黑名单中
     */
    public boolean isTokenBlacklisted(String token) {
        String blacklistKey = BLACKLIST_PREFIX + cryptoHelper.hashPassword(token).substring(0, 32);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey));
    }
}
