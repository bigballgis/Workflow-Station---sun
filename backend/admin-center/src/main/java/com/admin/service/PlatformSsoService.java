package com.admin.service;

import com.admin.config.PlatformSsoProperties;
import com.admin.dto.sso.SsoLoginRequest;
import com.admin.dto.sso.SsoLoginResponse;
import com.admin.dto.sso.SsoRedeemRequest;
import com.admin.dto.sso.SsoRedeemResponse;
import com.admin.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public SsoLoginResponse loginAndIssueCode(SsoLoginRequest request) {
        validateRedirectUri(request.getClientId(), request.getRedirectUri());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new IllegalArgumentException("Account is locked");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new IllegalArgumentException("Account is disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String code = UUID.randomUUID().toString();
        Payload payload = new Payload(user.getId(), request.getClientId(), request.getState());
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
                .state(request.getState())
                .redirectUri(request.getRedirectUri())
                .build();
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
