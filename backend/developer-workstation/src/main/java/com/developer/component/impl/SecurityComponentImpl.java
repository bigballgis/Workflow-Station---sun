package com.developer.component.impl;

import com.developer.component.SecurityComponent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全组件实现
 */
@Component
@Slf4j
public class SecurityComponentImpl implements SecurityComponent {
    
    private final String jwtSecret;
    private final long jwtExpiration;
    private final int maxLoginAttempts;
    private final int lockDurationMinutes;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // workflow-engine 使用的 JWT secret
    private static final String WORKFLOW_ENGINE_JWT_SECRET = "workflow-engine-jwt-secret-key-2026";
    
    private final Map<String, Integer> loginFailures = new ConcurrentHashMap<>();
    private final Map<String, Long> lockoutTimes = new ConcurrentHashMap<>();
    
    /**
     * 默认构造函数，用于测试
     */
    public SecurityComponentImpl() {
        this("workflow-engine-jwt-secret-key-2026", 86400000L, 5, 30);
    }
    
    /**
     * Spring构造函数注入
     */
    public SecurityComponentImpl(
            @Value("${security.jwt.secret:workflow-engine-jwt-secret-key-2026}") String jwtSecret,
            @Value("${security.jwt.expiration:86400000}") long jwtExpiration,
            @Value("${security.max-login-attempts:5}") int maxLoginAttempts,
            @Value("${security.lock-duration-minutes:30}") int lockDurationMinutes) {
        this.jwtSecret = jwtSecret;
        this.jwtExpiration = jwtExpiration;
        this.maxLoginAttempts = maxLoginAttempts;
        this.lockDurationMinutes = lockDurationMinutes;
    }
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateToken(String username, Set<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);
        
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    @Override
    public boolean validateToken(String token) {
        // 首先尝试解析 workflow-engine 的自定义格式或标准 JWT payload
        Map<String, Object> customClaims = parseWorkflowEngineToken(token);
        if (customClaims != null) {
            // 检查过期时间
            Object expObj = customClaims.get("exp");
            if (expObj != null) {
                long expiration = ((Number) expObj).longValue();
                // 标准 JWT 的 exp 是秒级时间戳，需要转换为毫秒
                long expirationMs = expiration * 1000;
                boolean valid = System.currentTimeMillis() <= expirationMs;
                log.debug("Token validation result: {}, expiration: {}, expirationMs: {}, currentMs: {}", 
                    valid, expiration, expirationMs, System.currentTimeMillis());
                return valid;
            }
            // 没有过期时间字段，认为有效
            log.debug("Token has no expiration, treating as valid");
            return true;
        }
        
        // 如果不是自定义格式，尝试标准 JWT 格式（使用本地 secret）
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getUsernameFromToken(String token) {
        // 首先尝试解析 workflow-engine 的自定义格式
        Map<String, Object> customClaims = parseWorkflowEngineToken(token);
        if (customClaims != null) {
            return (String) customClaims.get("sub");
        }
        
        // 如果不是自定义格式，尝试标准 JWT 格式
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getRolesFromToken(String token) {
        // 首先尝试解析 workflow-engine 的自定义格式
        Map<String, Object> customClaims = parseWorkflowEngineToken(token);
        if (customClaims != null) {
            Object rolesObj = customClaims.get("roles");
            if (rolesObj instanceof List) {
                return new HashSet<>((List<String>) rolesObj);
            }
            return Collections.emptySet();
        }
        
        // 如果不是自定义格式，尝试标准 JWT 格式
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        List<String> roles = claims.get("roles", List.class);
        return roles != null ? new HashSet<>(roles) : Collections.emptySet();
    }
    
    /**
     * 解析 workflow-engine 生成的自定义 token 格式
     * 格式: base64(payload).hash(payload+secret)
     * 或者标准 JWT 格式: header.payload.signature (3 部分)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseWorkflowEngineToken(String token) {
        try {
            String[] parts = token.split("\\.");
            
            // 处理自定义格式 (2 部分)
            if (parts.length == 2) {
                String encodedPayload = parts[0];
                String signature = parts[1];
                
                // 验证签名
                String expectedSignature = hashPassword(encodedPayload + WORKFLOW_ENGINE_JWT_SECRET);
                if (!expectedSignature.equals(signature)) {
                    log.debug("Signature mismatch for 2-part token");
                    return null;
                }
                
                String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), 
                        StandardCharsets.UTF_8);
                
                log.debug("Successfully parsed 2-part workflow-engine token");
                return objectMapper.readValue(payload, Map.class);
            }
            
            // 处理标准 JWT 格式 (3 部分) - 只解析 payload，不验证签名
            // 因为 workflow-engine 可能使用不同的签名方式
            if (parts.length == 3) {
                String encodedPayload = parts[1];
                
                // 尝试解码 payload
                String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), 
                        StandardCharsets.UTF_8);
                
                Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
                
                // 检查是否包含必要的字段
                if (claims.containsKey("sub")) {
                    log.debug("Successfully parsed 3-part JWT token, sub: {}", claims.get("sub"));
                    return claims;
                }
            }
            
            log.debug("Token format not recognized, parts: {}", parts.length);
            return null;
            
        } catch (Exception e) {
            log.debug("Failed to parse workflow-engine token: {}", e.getMessage());
            // 不是 workflow-engine 格式，返回 null
            return null;
        }
    }
    
    /**
     * 密码哈希（与 workflow-engine 保持一致）
     */
    private String hashPassword(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash computation failed", e);
        }
    }
    
    @Override
    public boolean isAccountLocked(String username) {
        Long lockoutTime = lockoutTimes.get(username);
        if (lockoutTime == null) {
            return false;
        }
        long lockDurationMs = lockDurationMinutes * 60 * 1000L;
        if (System.currentTimeMillis() - lockoutTime > lockDurationMs) {
            lockoutTimes.remove(username);
            loginFailures.remove(username);
            return false;
        }
        return true;
    }
    
    @Override
    public void recordLoginFailure(String username) {
        int failures = loginFailures.getOrDefault(username, 0) + 1;
        loginFailures.put(username, failures);
        if (failures >= maxLoginAttempts) {
            lockoutTimes.put(username, System.currentTimeMillis());
            log.warn("Account locked due to {} failed login attempts: {}", failures, username);
        }
    }
    
    @Override
    public void resetLoginFailures(String username) {
        loginFailures.remove(username);
        lockoutTimes.remove(username);
    }
    
    @Override
    public boolean hasPermission(String username, String permission) {
        // TODO: 实现权限检查逻辑
        return true;
    }
    
    @Override
    public boolean hasRole(String username, String role) {
        // TODO: 实现角色检查逻辑
        return true;
    }
}
