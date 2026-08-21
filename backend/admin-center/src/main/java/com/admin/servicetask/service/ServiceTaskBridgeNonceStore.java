package com.admin.servicetask.service;

import com.admin.servicetask.client.ServiceTaskApiClient.ApSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Activepieces 跨域登录桥的一次性 nonce 存储（方案 B：跨域 SSO 握手）。
 *
 * <p>背景：admin 域与 AP 网关域分属不同父域时，平台 JWT cookie（host-only）跟不到 AP 域，
 * 桥页无法在 AP 域用 cookie 换 token。解法是把"验平台身份 + 换 AP 会话"挪到 admin 域的
 * {@code /launch}（cookie 在自己域上有效），换好的 AP 会话以一个**一次性、短时效**的 nonce
 * 暂存于 Redis，nonce 经 URL fragment 带到 AP 域桥页，桥页凭 nonce 调 {@code /__ap/token} 取回会话。
 *
 * <p>安全：nonce 是不可猜的 UUID、单次消费（取出即删）、{@code nonceTtlSeconds} 后过期；
 * AP token 本身从不进入 URL，只在 nonce 兑换时由服务端返回。多副本安全（状态在 Redis 而非进程内）。
 * 复用平台既有的 {@link com.admin.service.PlatformSsoService} SSO code 范式（set+TTL → get+delete 单次）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceTaskBridgeNonceStore {

    private static final String KEY_PREFIX = "ap:bridge:nonce:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** Redis 暂存的 AP 会话载荷（token + projectId）。 */
    private record NoncePayload(String token, String projectId) {}

    /**
     * 签发一次性 nonce 并把 AP 会话落 Redis（短 TTL）。
     *
     * @param session  已按当前操作人换得的 AP 会话
     * @param ttlSeconds  有效期（秒），下限 30s
     * @return 不可猜的 nonce（写入桥页 URL fragment）
     */
    public String issue(ApSession session, int ttlSeconds) {
        String nonce = UUID.randomUUID().toString();
        try {
            String json = objectMapper.writeValueAsString(
                    new NoncePayload(session.token(), session.projectId()));
            stringRedisTemplate.opsForValue().set(
                    KEY_PREFIX + nonce, json, Duration.ofSeconds(Math.max(30, ttlSeconds)));
        } catch (Exception e) {
            // 不泄露 token 内容到日志
            throw new IllegalStateException("Failed to persist AP bridge nonce", e);
        }
        return nonce;
    }

    /**
     * 兑换 nonce 取回 AP 会话，并立即删除（单次消费）。无效 / 过期 / 已用 → 空。
     */
    public Optional<ApSession> consume(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return Optional.empty();
        }
        String key = KEY_PREFIX + nonce;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        // 单次消费：删除成功才算有效，避免并发重复兑换。
        Boolean deleted = stringRedisTemplate.delete(key);
        if (!Boolean.TRUE.equals(deleted)) {
            return Optional.empty();
        }
        try {
            NoncePayload payload = objectMapper.readValue(json, NoncePayload.class);
            return Optional.of(new ApSession(payload.token(), payload.projectId(), null));
        } catch (Exception e) {
            log.warn("Corrupt AP bridge nonce payload in redis");
            return Optional.empty();
        }
    }
}
