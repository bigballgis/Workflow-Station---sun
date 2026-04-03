package com.platform.security.service.impl;

import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.service.JwtTokenService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JWT Token Service implementation.
 * Validates: Requirements 2.6, 2.7, 2.8, 3.2, 3.3
 */
@Slf4j
@Service
public class JwtTokenServiceImpl implements JwtTokenService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_DISPLAY_NAME = "displayName";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_LANGUAGE = "language";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;
    private final SecretKey secretKey;

    public JwtTokenServiceImpl(JwtProperties jwtProperties, StringRedisTemplate redisTemplate) {
        this.jwtProperties = jwtProperties;
        this.redisTemplate = redisTemplate;
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set platform.security.jwt.secret (at least 32 bytes).");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret is only " + keyBytes.length + " bytes. Minimum 32 bytes required.");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateToken(String userId, String username, List<String> roles,
                                List<String> permissions, String language) {
        return generateToken(userId, username, null, null, roles, permissions, language);
    }

    @Override
    public String generateToken(String userId, String username, String email, String displayName,
                                List<String> roles, List<String> permissions, String language) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getExpirationMs());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_DISPLAY_NAME, displayName)
                .claim(CLAIM_ROLES, roles != null ? roles : Collections.emptyList())
                .claim(CLAIM_PERMISSIONS, permissions != null ? permissions : Collections.emptyList())
                .claim(CLAIM_LANGUAGE, language != null ? language : "zh_CN")
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    @Override
    public String generateToken(UserPrincipal principal) {
        return generateToken(
                principal.getUserId(),
                principal.getUsername(),
                principal.getEmail(),
                principal.getDisplayName(),
                principal.getRoles(),
                principal.getPermissions(),
                principal.getLanguage()
        );
    }

    @Override
    public String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getRefreshExpirationMs());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            if (isBlacklisted(token)) {
                log.debug("Token is blacklisted");
                return false;
            }
            
            Jws<Claims> claims = parseToken(token);
            return claims != null && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token).getPayload();
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            log.debug("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public UserPrincipal extractUserPrincipal(String token) {
        Claims claims = parseToken(token).getPayload();
        
        // Fallback to sub claim for tokens generated by AuthControllers (which only set sub, not userId)
        String userId = claims.get(CLAIM_USER_ID, String.class);
        if (userId == null) {
            userId = claims.getSubject();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get(CLAIM_PERMISSIONS, List.class);

        return UserPrincipal.builder()
                .userId(userId)
                .username(claims.get(CLAIM_USERNAME, String.class))
                .email(claims.get(CLAIM_EMAIL, String.class))
                .displayName(claims.get(CLAIM_DISPLAY_NAME, String.class))
                .roles(roles != null ? roles : Collections.emptyList())
                .permissions(permissions != null ? permissions : Collections.emptyList())
                .language(claims.get(CLAIM_LANGUAGE, String.class))
                .build();
    }

    @Override
    public String extractUserId(String token) {
        Claims claims = parseToken(token).getPayload();
        String userId = claims.get(CLAIM_USER_ID, String.class);
        return userId != null ? userId : claims.getSubject();
    }

    @Override
    public String refreshToken(String refreshToken) {
        if (!validateToken(refreshToken)) {
            throw new JwtException("Invalid refresh token");
        }

        Claims claims = parseToken(refreshToken).getPayload();
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        
        if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new JwtException("Token is not a refresh token");
        }

        String userId = claims.get(CLAIM_USER_ID, String.class);
        
        // Generate new access token with minimal claims
        // The caller should fetch fresh user data and generate a proper token
        return generateToken(userId, null, null, null, null);
    }

    @Override
    public long getExpirationTime(String token) {
        Claims claims = parseToken(token).getPayload();
        return claims.getExpiration().getTime();
    }

    @Override
    public long getRemainingValiditySeconds(String token) {
        try {
            long expirationTime = getExpirationTime(token);
            long remaining = (expirationTime - System.currentTimeMillis()) / 1000;
            return Math.max(0, remaining);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void blacklistToken(String token) {
        try {
            long remainingSeconds = getRemainingValiditySeconds(token);
            if (remainingSeconds > 0) {
                String tokenHash = hashToken(token);
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + tokenHash,
                        "1",
                        remainingSeconds,
                        TimeUnit.SECONDS
                );
                log.debug("Token blacklisted with TTL: {} seconds", remainingSeconds);
            }
        } catch (Exception e) {
            log.error("Failed to blacklist token: {}", e.getMessage());
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        try {
            String tokenHash = hashToken(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + tokenHash));
        } catch (Exception e) {
            // Fail-closed: if Redis is unavailable, reject the token rather than accepting a possibly revoked JWT
            log.error("Failed to check token blacklist, denying token: {}", e.getMessage());
            return true;
        }
    }

    private Jws<Claims> parseToken(String token) {
        JwtParserBuilder parserBuilder = Jwts.parser()
                .verifyWith(secretKey);
        
        if (jwtProperties.isValidateIssuer()) {
            parserBuilder.requireIssuer(jwtProperties.getIssuer());
        }
        
        return parserBuilder.build().parseSignedClaims(token);
    }

    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
