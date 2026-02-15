package com.portal.controller;

import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import com.platform.security.service.UserRoleService;
import com.platform.common.i18n.I18nService;
import com.portal.dto.LoginRequest;
import com.portal.dto.LoginResponse;
import com.portal.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final UserRoleService userRoleService;
    private final I18nService i18nService;
    
    @Value("${jwt.secret:my-super-secret-jwt-key-for-development-only-32chars}")
    private String jwtSecret;
    
    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        log.info("Login attempt for user: {} from {}", request.getUsername(), ipAddress);
        
        try {
            log.debug("Looking up user: {}", request.getUsername());
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.invalid_credentials")));
            
            log.debug("User found: {}, status: {}", user.getUsername(), user.getStatus());
            
            if (user.isLocked()) {
                log.warn("User {} is locked", user.getUsername());
                throw new RuntimeException(i18nService.getMessage("auth.account_locked"));
            }
            
            if (UserStatus.INACTIVE.equals(user.getStatus())) {
                log.warn("User {} is inactive", user.getUsername());
                throw new RuntimeException(i18nService.getMessage("auth.account_disabled"));
            }
            
            log.debug("Checking password for user: {}", user.getUsername());
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                log.warn("Password mismatch for user: {}", user.getUsername());
                user.incrementFailedLoginCount();
                if (user.getFailedLoginCount() >= 5) {
                    user.setStatus(UserStatus.LOCKED);
                    user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
                }
                userRepository.save(user);
                throw new RuntimeException(i18nService.getMessage("auth.invalid_credentials"));
            }
            
            log.debug("Password matched, updating login info");
            user.resetFailedLoginCount();
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(ipAddress);
            userRepository.save(user);
            
            // 使用 UserRoleService 获取用户有效角色
            List<UserEffectiveRole> effectiveRoles = userRoleService.getEffectiveRolesForUser(user.getId().toString());
            List<String> roles = effectiveRoles.stream()
                    .map(UserEffectiveRole::getRoleCode)
                    .distinct()
                    .collect(Collectors.toList());
            
            // 如果没有从新系统获取到角色，回退到旧方式
            if (roles.isEmpty()) {
                roles = getRolesForUserLegacy(user.getId());
            }
            
            List<String> permissions = getPermissionsForRoles(roles);
            List<LoginResponse.RoleWithSource> rolesWithSources = buildRolesWithSources(effectiveRoles);
            
            String accessToken = generateToken(user, roles, permissions);
            String refreshToken = generateRefreshToken(user.getId().toString());
            
            log.info("User {} logged in successfully", request.getUsername());
            
            return ResponseEntity.ok(LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtExpiration / 1000)
                    .user(LoginResponse.UserLoginInfo.builder()
                            .userId(user.getId().toString())
                            .username(user.getUsername())
                            .displayName(user.getFullName() != null && !user.getFullName().isEmpty() 
                                ? user.getFullName() 
                                : (user.getDisplayName() != null && !user.getDisplayName().isEmpty() 
                                    ? user.getDisplayName() 
                                    : user.getUsername()))
                            .email(user.getEmail())
                            .roles(roles)
                            .permissions(permissions)
                            .rolesWithSources(rolesWithSources)
                            .language(user.getLanguage())
                            .build())
                    .build());
        } catch (RuntimeException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(LoginResponse.builder().build());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse.UserLoginInfo> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        try {
            String token = authHeader.substring(7);
            Claims claims = parseToken(token);
            String userId = claims.getSubject();
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.user_not_found")));
            
            List<UserEffectiveRole> effectiveRoles = userRoleService.getEffectiveRolesForUser(userId);
            List<String> roles = effectiveRoles.stream()
                    .map(UserEffectiveRole::getRoleCode)
                    .distinct()
                    .collect(Collectors.toList());
            
            if (roles.isEmpty()) {
                roles = getRolesForUserLegacy(user.getId());
            }
            
            List<LoginResponse.RoleWithSource> rolesWithSources = buildRolesWithSources(effectiveRoles);
            
            return ResponseEntity.ok(LoginResponse.UserLoginInfo.builder()
                    .userId(user.getId().toString())
                    .username(user.getUsername())
                    .displayName(user.getFullName() != null && !user.getFullName().isEmpty() 
                        ? user.getFullName() 
                        : (user.getDisplayName() != null && !user.getDisplayName().isEmpty() 
                            ? user.getDisplayName() 
                            : user.getUsername()))
                    .email(user.getEmail())
                    .roles(roles)
                    .permissions(getPermissionsForRoles(roles))
                    .rolesWithSources(rolesWithSources)
                    .language(user.getLanguage())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> validateToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.ok(false);
        }
        try {
            parseToken(authHeader.substring(7));
            return ResponseEntity.ok(true);
        } catch (Exception e) {
            return ResponseEntity.ok(false);
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
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("displayName", user.getFullName() != null && !user.getFullName().isEmpty() 
                    ? user.getFullName() 
                    : (user.getDisplayName() != null && !user.getDisplayName().isEmpty() 
                        ? user.getDisplayName() 
                        : user.getUsername()))
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("language", user.getLanguage())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    private String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 604800000);
        
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

    private List<String> getRolesForUserLegacy(String userId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT r.code FROM sys_role_assignments ra " +
                    "JOIN sys_roles r ON ra.role_id = r.id " +
                    "WHERE ra.target_type = 'USER' AND ra.target_id = ? AND r.status = 'ACTIVE' " +
                    "AND (ra.valid_from IS NULL OR ra.valid_from <= NOW()) " +
                    "AND (ra.valid_to IS NULL OR ra.valid_to >= NOW())",
                    String.class, userId);
        } catch (Exception e) {
            return List.of("USER");
        }
    }

    private List<String> getPermissionsForRoles(List<String> roles) {
        List<String> permissions = new ArrayList<>();
        for (String role : roles) {
            switch (role) {
                case "MANAGER" -> permissions.addAll(List.of("task:read", "task:write", "task:approve", "team:manage"));
                case "USER" -> permissions.addAll(List.of("task:read", "task:write", "process:start"));
                default -> permissions.add("basic:access");
            }
        }
        return permissions.stream().distinct().toList();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
