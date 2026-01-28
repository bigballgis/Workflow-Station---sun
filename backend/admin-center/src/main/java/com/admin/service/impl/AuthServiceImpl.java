package com.admin.service.impl;

import com.admin.dto.request.LoginRequest;
import com.admin.dto.response.LoginResponse;
import com.admin.entity.User;
import com.admin.enums.UserStatus;
import com.admin.repository.UserRepository;
import com.admin.service.AuthService;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.service.UserRoleService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleService userRoleService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.admin.service.TaskAssignmentQueryService taskAssignmentQueryService;
    
    @Value("${jwt.secret:my-super-secret-jwt-key-for-development-only-32chars}")
    private String jwtSecret;
    
    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;
    
    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.debug("Login attempt for user: {}", request.getUsername());
        
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("User not found: {}", request.getUsername());
                    return new RuntimeException("Invalid username or password");
                });
        
        if (user.getStatus() == UserStatus.LOCKED) {
            log.warn("Account locked: {}", request.getUsername());
            throw new RuntimeException("Account is locked");
        }
        
        if (user.getStatus() == UserStatus.DISABLED) {
            log.warn("Account disabled: {}", request.getUsername());
            throw new RuntimeException("Account is disabled");
        }
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Invalid password for user: {}", request.getUsername());
            user.incrementFailedLoginCount();
            
            if (user.getFailedLoginCount() >= 5) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
            }
            userRepository.save(user);
            throw new RuntimeException("Invalid username or password");
        }
        
        List<String> userRoleCodes;
        try {
            userRoleCodes = getUserRoleCodes(user.getId());
        } catch (Exception e) {
            log.error("Failed to get user roles for {}: {}", request.getUsername(), e.getMessage());
            throw new RuntimeException("Failed to get user roles");
        }
        
        boolean hasAdminAccess = userRoleCodes.stream()
                .anyMatch(code -> "SYS_ADMIN".equals(code) || "AUDITOR".equals(code));
        
        if (!hasAdminAccess) {
            log.warn("User {} does not have admin center access. Roles: {}", request.getUsername(), userRoleCodes);
            throw new RuntimeException("You do not have access to Admin Center");
        }
        
        user.resetFailedLoginCount();
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);
        
        List<String> roles = userRoleCodes;
        List<String> permissions = getPermissionsForRoles(roles);
        
        List<LoginResponse.RoleWithSource> rolesWithSources = roles.stream()
                .map(code -> LoginResponse.RoleWithSource.builder()
                        .roleCode(code)
                        .roleName(code)
                        .sourceType(null)
                        .sourceId(user.getId())
                        .sourceName("Direct Assignment")
                        .build())
                .collect(Collectors.toList());
        
        String accessToken = generateToken(user, roles, permissions);
        String refreshToken = generateRefreshToken(user.getId());
        
        log.info("User {} logged in successfully from {}", request.getUsername(), ipAddress);
        
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtExpiration / 1000)
                .user(LoginResponse.UserLoginInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .email(user.getEmail())
                        .roles(roles)
                        .permissions(permissions)
                        .rolesWithSources(rolesWithSources)
                        .businessUnitId(taskAssignmentQueryService.getUserBusinessUnitId(user.getId()))
                        .language(user.getLanguage())
                        .build())
                .build();
    }

    @Override
    public void logout(String token) {
        log.info("User logged out");
    }

    @Override
    public LoginResponse.UserLoginInfo refreshToken(String refreshToken) {
        try {
            Claims claims = parseToken(refreshToken);
            String userId = claims.getSubject();
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            List<UserEffectiveRole> effectiveRoles = userRoleService.getEffectiveRolesForUser(user.getId());
            List<String> roles = effectiveRoles.stream()
                    .map(UserEffectiveRole::getRoleCode)
                    .distinct()
                    .collect(Collectors.toList());
            
            List<LoginResponse.RoleWithSource> rolesWithSources = buildRolesWithSources(effectiveRoles);
            
            return LoginResponse.UserLoginInfo.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .email(user.getEmail())
                    .roles(roles)
                    .permissions(getPermissionsForRoles(roles))
                    .rolesWithSources(rolesWithSources)
                    .businessUnitId(taskAssignmentQueryService.getUserBusinessUnitId(user.getId()))
                    .language(user.getLanguage())
                    .build();
        } catch (Exception e) {
            log.error("Failed to refresh token", e);
            throw new RuntimeException("Token refresh failed");
        }
    }

    @Override
    public LoginResponse.UserLoginInfo getCurrentUser(String token) {
        try {
            Claims claims = parseToken(token);
            String userId = claims.getSubject();
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            List<UserEffectiveRole> effectiveRoles = userRoleService.getEffectiveRolesForUser(user.getId());
            List<String> roles = effectiveRoles.stream()
                    .map(UserEffectiveRole::getRoleCode)
                    .distinct()
                    .collect(Collectors.toList());
            
            List<LoginResponse.RoleWithSource> rolesWithSources = buildRolesWithSources(effectiveRoles);
            
            return LoginResponse.UserLoginInfo.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .email(user.getEmail())
                    .roles(roles)
                    .permissions(getPermissionsForRoles(roles))
                    .rolesWithSources(rolesWithSources)
                    .businessUnitId(taskAssignmentQueryService.getUserBusinessUnitId(user.getId()))
                    .language(user.getLanguage())
                    .build();
        } catch (Exception e) {
            log.error("Failed to get current user", e);
            throw new RuntimeException("Failed to get user info");
        }
    }

    @Override
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private List<LoginResponse.RoleWithSource> buildRolesWithSources(List<UserEffectiveRole> effectiveRoles) {
        List<LoginResponse.RoleWithSource> result = new ArrayList<>();
        
        for (UserEffectiveRole role : effectiveRoles) {
            for (var source : role.getSources()) {
                result.add(LoginResponse.RoleWithSource.builder()
                        .roleCode(role.getRoleCode())
                        .roleName(role.getRoleName())
                        .sourceType(source.getSourceType())
                        .sourceId(source.getSourceId())
                        .sourceName(source.getSourceName())
                        .build());
            }
        }
        
        return result;
    }

    private String generateToken(User user, List<String> roles, List<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpiration);
        
        return Jwts.builder()
                .subject(user.getId())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("displayName", user.getDisplayName())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("businessUnitId", taskAssignmentQueryService.getUserBusinessUnitId(user.getId()))
                .claim("language", user.getLanguage())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    private String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpiration);
        
        return Jwts.builder()
                .subject(userId)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private List<String> getPermissionsForRoles(List<String> roles) {
        List<String> permissions = new ArrayList<>();
        
        for (String role : roles) {
            switch (role) {
                case "SYS_ADMIN", "SUPER_ADMIN", "ADMIN", "SYS_ADAMIN_ROLE" -> permissions.addAll(List.of(
                        "user:read", "user:write", "user:delete",
                        "role:read", "role:write", "role:delete",
                        "system:admin"
                ));
                case "SYSTEM_ADMIN" -> permissions.addAll(List.of(
                        "user:read", "user:write",
                        "role:read", "role:write",
                        "system:config"
                ));
                case "TENANT_ADMIN" -> permissions.addAll(List.of(
                        "user:read", "user:write",
                        "tenant:admin"
                ));
                case "AUDITOR" -> permissions.addAll(List.of(
                        "audit:read", "log:read"
                ));
                default -> permissions.add("basic:access");
            }
        }
        
        return permissions.stream().distinct().toList();
    }
    
    private List<String> getUserRoleCodes(String userId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT r.code FROM sys_role_assignments ra " +
                    "JOIN sys_roles r ON ra.role_id = r.id " +
                    "WHERE ra.target_type = 'USER' AND ra.target_id = ? AND r.status = 'ACTIVE' " +
                    "AND (ra.valid_from IS NULL OR ra.valid_from <= NOW()) " +
                    "AND (ra.valid_to IS NULL OR ra.valid_to >= NOW())",
                    String.class,
                    userId
            );
        } catch (Exception e) {
            log.warn("Failed to get role codes for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }
}