package com.platform.security.service.impl;

import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.exception.AuthenticationException;
import com.platform.common.enums.ErrorCode;
import com.platform.security.config.JwtProperties;
import com.platform.security.service.JwtTokenService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JWT Token Service implementation using JJWT library.
 * Validates: Requirements 3.1, 3.2, 3.3
 */
@Slf4j
@Service
public class JwtTokenServiceImpl implements JwtTokenService {
    
    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;
    
    public JwtTokenServiceImpl(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }
    
    @Override
    public String generateToken(String userId, String username, List<String> roles,
                                List<String> permissions, String departmentId, String language) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpirationMs());
        
        Map<String, Object> claims = new HashMap<>();
        claims.put(PlatformConstants.JWT_CLAIM_USER_ID, userId);
        claims.put(PlatformConstants.JWT_CLAIM_USERNAME, username);
        claims.put(PlatformConstants.JWT_CLAIM_ROLES, roles != null ? roles : List.of());
        claims.put(PlatformConstants.JWT_CLAIM_PERMISSIONS, permissions != null ? permissions : List.of());
        claims.put(PlatformConstants.JWT_CLAIM_DEPARTMENT_ID, departmentId);
        claims.put(PlatformConstants.JWT_CLAIM_LANGUAGE, language != null ? language : PlatformConstants.DEFAULT_LANGUAGE);
        
        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }
    
    @Override
    public String generateToken(UserPrincipal principal) {
        return generateToken(
                principal.getUserId(),
                principal.getUsername(),
                principal.getRoles(),
                principal.getPermissions(),
                principal.getDepartmentId(),
                principal.getLanguage()
        );
    }
    
    @Override
    public String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshExpirationMs());
        
        return Jwts.builder()
                .subject(userId)
                .claim("type", "refresh")
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }
    
    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }
    
    @Override
    public UserPrincipal extractUserPrincipal(String token) {
        Claims claims = extractAllClaims(token);
        
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(PlatformConstants.JWT_CLAIM_ROLES, List.class);
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get(PlatformConstants.JWT_CLAIM_PERMISSIONS, List.class);
        
        return UserPrincipal.builder()
                .userId(claims.get(PlatformConstants.JWT_CLAIM_USER_ID, String.class))
                .username(claims.get(PlatformConstants.JWT_CLAIM_USERNAME, String.class))
                .roles(roles != null ? roles : List.of())
                .permissions(permissions != null ? permissions : List.of())
                .departmentId(claims.get(PlatformConstants.JWT_CLAIM_DEPARTMENT_ID, String.class))
                .language(claims.get(PlatformConstants.JWT_CLAIM_LANGUAGE, String.class))
                .build();
    }
    
    @Override
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }
    
    @Override
    public String refreshToken(String refreshToken) {
        Claims claims = extractAllClaims(refreshToken);
        
        String tokenType = claims.get("type", String.class);
        if (!"refresh".equals(tokenType)) {
            throw new AuthenticationException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID, 
                    "Invalid refresh token type");
        }
        
        if (claims.getExpiration().before(new Date())) {
            throw new AuthenticationException(ErrorCode.AUTH_TOKEN_EXPIRED, 
                    "Refresh token has expired");
        }
        
        String userId = claims.getSubject();
        // In a real implementation, we would fetch fresh user data from the database
        // For now, we generate a new token with the same user ID
        return generateToken(userId, userId, List.of(), List.of(), null, PlatformConstants.DEFAULT_LANGUAGE);
    }
    
    @Override
    public long getExpirationTime(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getExpiration().getTime();
    }
    
    @Override
    public long getRemainingValiditySeconds(String token) {
        try {
            long expirationTime = getExpirationTime(token);
            long currentTime = System.currentTimeMillis();
            long remaining = (expirationTime - currentTime) / 1000;
            return Math.max(0, remaining);
        } catch (JwtException e) {
            return 0;
        }
    }
    
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new AuthenticationException(ErrorCode.AUTH_TOKEN_EXPIRED, "Token has expired");
        } catch (JwtException e) {
            throw new AuthenticationException(ErrorCode.AUTH_TOKEN_INVALID, "Invalid token: " + e.getMessage());
        }
    }
}
