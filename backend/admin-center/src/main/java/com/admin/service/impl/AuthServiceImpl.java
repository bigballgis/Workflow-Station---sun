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

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleService userRoleService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    
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
        
        // 查找用户
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("User not found: {}", request.getUsername());
                    return new RuntimeException("用户名或密码错误");
                });
        
        // 检查用户状态
        if (user.getStatus() == UserStatus.LOCKED) {
            log.warn("Account locked: {}", request.getUsername());
            throw new RuntimeException("账户已被锁定");
        }
        
        if (user.getStatus() == UserStatus.DISABLED) {
            log.warn("Account disabled: {}", request.getUsername());
            throw new RuntimeException("账户已被禁用");
        }
        
        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Invalid password for user: {}", request.getUsername());
            user.incrementFailedLoginCount();
            
            // 如果失败次数超过5次，锁定账户
            if (user.getFailedLoginCount() >= 5) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
            }
            userRepository.save(user);
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 检查用户是否有 SYS_ADMIN 或 AUDITOR 角色（Admin Center 专用）
        List<String> userRoleCodes;
        try {
            userRoleCodes = getUserRoleCodes(user.getId());
        } catch (Exception e) {
            log.error("Failed to get user roles for {}: {}", request.getUsername(), e.getMessage());
            throw new RuntimeException("获取用户角色失败，请稍后重试");
        }
        
        boolean hasAdminAccess = userRoleCodes.stream()
                .anyMatch(code -> "SYS_ADMIN".equals(code) || "AUDITOR".equals(code));
        
        if (!hasAdminAccess) {
            log.warn("User {} does not have admin center access. Roles: {}", request.getUsername(), userRoleCodes);
            throw new RuntimeException("您没有管理员中心的访问权限");
        }
        
        // 重置失败次数，更新登录信息
        user.resetFailedLoginCount();
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);
        
        // 获取用户有效角色 - 使用简单查询避免复杂的 UserRoleService
        List<String> roles = userRoleCodes;
        
        // 获取权限
        List<String> permissions = getPermissionsForRoles(roles);
        
        // 构建简单的 rolesWithSources
        List<LoginResponse.RoleWithSource> rolesWithSources = roles.stream()
                .map(code -> LoginResponse.RoleWithSource.builder()
                        .roleCode(code)
                        .roleName(code)
                        .sourceType(null)
                        .sourceId(user.getId())
                        .sourceName("直接分配")
                        .build())
                .collect(Collectors.toList());
        
        // 生成令牌
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
                        .departmentId(user.getDepartmentId())
                        .language(user.getLanguage())
                        .build())
                .build();
    }


    @Override
    public void logout(String token) {
        // 简单实现：前端清除令牌即可
        // 生产环境应该将令牌加入黑名单
        log.info("User logged out");
    }

    @Override
    public LoginResponse.UserLoginInfo refreshToken(String refreshToken) {
        try {
            Claims claims = parseToken(refreshToken);
            String userId = claims.getSubject();
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
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
                    .departmentId(user.getDepartmentId())
                    .language(user.getLanguage())
                    .build();
        } catch (Exception e) {
            log.error("Failed to refresh token", e);
            throw new RuntimeException("令牌刷新失败");
        }
    }

    @Override
    public LoginResponse.UserLoginInfo getCurrentUser(String token) {
        try {
            Claims claims = parseToken(token);
            String userId = claims.getSubject();
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
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
                    .departmentId(user.getDepartmentId())
                    .language(user.getLanguage())
                    .build();
        } catch (Exception e) {
            log.error("Failed to get current user", e);
            throw new RuntimeException("获取用户信息失败");
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
    
    /**
     * 构建角色及来源信息列表
     */
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
                .claim("departmentId", user.getDepartmentId())
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
                case "SYS_ADMIN", "SUPER_ADMIN", "ADMIN" -> permissions.addAll(List.of(
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
    
    /**
     * 获取用户的角色代码列表（用于登录权限检查）
     * 使用 sys_role_assignments 表
     */
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
