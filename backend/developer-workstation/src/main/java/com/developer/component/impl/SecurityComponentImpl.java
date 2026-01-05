package com.developer.component.impl;

import com.developer.component.SecurityComponent;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
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
    
    private final Map<String, Integer> loginFailures = new ConcurrentHashMap<>();
    private final Map<String, Long> lockoutTimes = new ConcurrentHashMap<>();
    
    /**
     * 默认构造函数，用于测试
     */
    public SecurityComponentImpl() {
        this("defaultSecretKeyForDeveloperWorkstationModule123456", 86400000L, 5, 30);
    }
    
    /**
     * Spring构造函数注入
     */
    public SecurityComponentImpl(
            @Value("${jwt.secret:defaultSecretKeyForDeveloperWorkstationModule123456}") String jwtSecret,
            @Value("${jwt.expiration:86400000}") long jwtExpiration,
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
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        List<String> roles = claims.get("roles", List.class);
        return roles != null ? new HashSet<>(roles) : Collections.emptySet();
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
