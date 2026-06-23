package com.admin.service.impl;

import com.admin.config.AdminAuthProperties;
import com.admin.dto.request.LoginRequest;
import com.admin.dto.response.LoginResponse;
import com.admin.ldap.LdapAuthenticator;
import com.admin.ldap.LdapConstants;
import com.platform.security.entity.User;
import com.platform.security.model.UserStatus;
import com.admin.repository.UserRepository;
import com.admin.service.AuthService;
import com.platform.security.config.JwtProperties;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.service.JwtTokenService;
import com.platform.security.service.UserRoleService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

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
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    /** LDAP 认证器仅 {@code ldap.enabled=true} 时存在，故用 ObjectProvider 可选注入。 */
    private final ObjectProvider<LdapAuthenticator> ldapAuthenticatorProvider;
    private final AdminAuthProperties adminAuthProperties;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent, HttpServletResponse response) {
        log.debug("Login attempt for user: {}", request.getUsername());

        // LDAP 为权威源：优先走 LDAP bind 认证（成功即 JIT 回写 sys_users）；
        // LDAP 关闭 / 用户不在 LDAP / LDAP 不可用时回退本地账号密码。
        User user = authenticate(request);

        // JIT 后仍按 sys_users 状态做最终拦截（LDAP lockoutTime 已推导为 LOCKED/INACTIVE）
        assertAccountActive(user, request.getUsername());

        // Align with refresh / platform-security: UserRoleService (sys_role_assignments + virtual group members)
        List<String> userRoleCodes = userRoleService.getEffectiveRoleCodesForUser(user.getId());

        ensureAdminAccess(user, userRoleCodes);

        user.resetFailedLoginCount();
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);
        
        List<String> roles = userRoleCodes;
        List<String> permissions = getPermissionsForRoles(roles);
        
        Map<String, String> roleCodeToName = getRoleNames(roles);
        
        List<LoginResponse.RoleWithSource> rolesWithSources = roles.stream()
                .map(code -> LoginResponse.RoleWithSource.builder()
                        .roleCode(code)
                        .roleName(roleCodeToName.getOrDefault(code, code))
                        .sourceType(null)
                        .sourceId(user.getId())
                        .sourceName("Direct Assignment")
                        .build())
                .collect(Collectors.toList());
        
        String displayName = resolveDisplayName(user);
        String accessToken = jwtTokenService.generateToken(
                user.getId(), user.getUsername(), user.getEmail(), displayName,
                roles, permissions, user.getLanguage());
        String refreshToken = jwtTokenService.generateRefreshToken(user.getId());
        
        // Set httpOnly cookies for access token and refresh token
        // Service-specific cookie name (e.g. ac_access_token) so three apps on same origin do not overwrite each other. See JwtProperties#cookieNames.
        setAuthCookie(response, jwtProperties.getPrimaryCookieName(), accessToken, (int)(jwtProperties.getExpirationMs() / 1000));
        setAuthCookie(response, jwtProperties.getRefreshCookieName(), refreshToken, 7 * 24 * 60 * 60);
        
        log.info("User {} logged in successfully from {}", request.getUsername(), ipAddress);
        
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProperties.getExpirationMs() / 1000)
                .user(LoginResponse.UserLoginInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .displayName(displayName)
                        .email(user.getEmail())
                        .roles(roles)
                        .permissions(permissions)
                        .rolesWithSources(rolesWithSources)
                        // Set when user has a single UBR business unit; null with multiple BUs and no preferred (same as TaskAssignmentQueryService)
                        .businessUnitId(taskAssignmentQueryService.getUserBusinessUnitId(user.getId()))
                        .language(user.getLanguage())
                        .build())
                .build();
    }

    @Override
    @Transactional
    public LoginResponse issueSsoSession(String userId, String ipAddress, String userAgent, HttpServletResponse response) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new RuntimeException("Account is locked");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new RuntimeException("Account is disabled");
        }

        List<String> userRoleCodes = userRoleService.getEffectiveRoleCodesForUser(user.getId());
        ensureAdminAccess(user, userRoleCodes);

        user.resetFailedLoginCount();
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);

        List<String> roles = userRoleCodes;
        List<String> permissions = getPermissionsForRoles(roles);
        Map<String, String> roleCodeToName = getRoleNames(roles);
        List<LoginResponse.RoleWithSource> rolesWithSources = roles.stream()
                .map(code -> LoginResponse.RoleWithSource.builder()
                        .roleCode(code)
                        .roleName(roleCodeToName.getOrDefault(code, code))
                        .sourceType(null)
                        .sourceId(user.getId())
                        .sourceName("Direct Assignment")
                        .build())
                .collect(Collectors.toList());

        String displayName = resolveDisplayName(user);
        String accessToken = jwtTokenService.generateToken(
                user.getId(), user.getUsername(), user.getEmail(), displayName,
                roles, permissions, user.getLanguage());
        String refreshToken = jwtTokenService.generateRefreshToken(user.getId());

        // Set httpOnly cookies for access token and refresh token
        // Service-specific cookie name (e.g. ac_access_token) so three apps on same origin do not overwrite each other. See JwtProperties#cookieNames.
        setAuthCookie(response, jwtProperties.getPrimaryCookieName(), accessToken, (int)(jwtProperties.getExpirationMs() / 1000));
        setAuthCookie(response, jwtProperties.getRefreshCookieName(), refreshToken, 7 * 24 * 60 * 60);

        log.info("User {} SSO session issued from {}", user.getUsername(), ipAddress);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProperties.getExpirationMs() / 1000)
                .user(LoginResponse.UserLoginInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .displayName(displayName)
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
        jwtTokenService.blacklistToken(token);
        log.info("User logged out");
    }

    @Override
    public LoginResponse refreshLogin(String refreshToken, HttpServletResponse response) {
        try {
            if (!jwtTokenService.validateToken(refreshToken)) {
                throw new RuntimeException("Invalid or expired refresh token");
            }
            jwtTokenService.blacklistToken(refreshToken);

            String userId = jwtTokenService.extractUserId(refreshToken);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<UserEffectiveRole> effectiveRoles = userRoleService.getEffectiveRolesForUser(user.getId());
            List<String> roles = effectiveRoles.stream()
                    .map(UserEffectiveRole::getRoleCode)
                    .distinct()
                    .collect(Collectors.toList());

            List<LoginResponse.RoleWithSource> rolesWithSources = buildRolesWithSources(effectiveRoles);

            List<String> permissions = getPermissionsForRoles(roles);
            String displayName = resolveDisplayName(user);
            String accessToken = jwtTokenService.generateToken(
                    user.getId(), user.getUsername(), user.getEmail(), displayName,
                    roles, permissions, user.getLanguage());
            String newRefreshToken = jwtTokenService.generateRefreshToken(user.getId());

            // Set httpOnly cookies for new tokens
            // Service-specific cookie name (e.g. ac_access_token) so three apps on same origin do not overwrite each other. See JwtProperties#cookieNames.
            setAuthCookie(response, jwtProperties.getPrimaryCookieName(), accessToken, (int)(jwtProperties.getExpirationMs() / 1000));
            setAuthCookie(response, jwtProperties.getRefreshCookieName(), newRefreshToken, 7 * 24 * 60 * 60);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(newRefreshToken)
                    .expiresIn(jwtProperties.getExpirationMs() / 1000)
                    .user(LoginResponse.UserLoginInfo.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .displayName(displayName)
                            .email(user.getEmail())
                            .roles(roles)
                            .permissions(permissions)
                            .rolesWithSources(rolesWithSources)
                            // Set when user has a single UBR business unit; null with multiple BUs and no preferred
                            .businessUnitId(taskAssignmentQueryService.getUserBusinessUnitId(user.getId()))
                            .language(user.getLanguage())
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Failed to refresh token", e);
            throw new RuntimeException("Token refresh failed");
        }
    }

    @Override
    public LoginResponse.UserLoginInfo getCurrentUser(String token) {
        try {
            String userId = jwtTokenService.extractUserId(token);
            
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
                    .displayName(resolveDisplayName(user))
                    .email(user.getEmail())
                    .roles(roles)
                    .permissions(getPermissionsForRoles(roles))
                    .rolesWithSources(rolesWithSources)
                    // Set when user has a single UBR business unit; null with multiple BUs and no preferred
                    .businessUnitId(taskAssignmentQueryService.getUserBusinessUnitId(user.getId()))
                    .language(user.getLanguage())
                    .build();
        } catch (Exception e) {
            log.error("Failed to get current user", e);
            throw new RuntimeException("Failed to get user info");
        }
    }

    @Override
    public String generateAccessTokenForUser(LoginResponse.UserLoginInfo userInfo) {
        User user = userRepository.findById(userInfo.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        String displayName = resolveDisplayName(user);
        return jwtTokenService.generateToken(
                user.getId(), user.getUsername(), user.getEmail(), displayName,
                userInfo.getRoles(), userInfo.getPermissions(), user.getLanguage());
    }

    @Override
    public boolean validateToken(String token) {
        return jwtTokenService.validateToken(token);
    }

    @Override
    @Transactional
    public void changePassword(String accessToken, String oldPassword, String newPassword) {
        if (!jwtTokenService.validateToken(accessToken)) {
            throw new RuntimeException("INVALID_TOKEN");
        }
        String userId = jwtTokenService.extractUserId(accessToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new RuntimeException("INVALID_OLD_PASSWORD");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        jwtTokenService.blacklistToken(accessToken);
        log.info("Password changed for user {}", user.getUsername());
    }
    
    /**
     * 认证用户：LDAP 优先（权威源），失败语义决定是否回退本地。
     *
     * <ul>
     *   <li>AUTHENTICATED：LDAP bind 通过且已 JIT，按 userId 取回库内用户。</li>
     *   <li>NOT_IN_LDAP / UNAVAILABLE：回退本地账号密码（兼容本地管理员/LDAP 故障）。</li>
     *   <li>BAD_CREDENTIALS：用户在 LDAP 但口令错误——权威拒绝，不回退（记一次本地失败计数）。</li>
     * </ul>
     */
    private User authenticate(LoginRequest request) {
        LdapAuthenticator ldap = ldapAuthenticatorProvider.getIfAvailable();
        if (ldap != null) {
            LdapAuthenticator.LdapAuthResult result =
                    ldap.authenticate(request.getUsername(), request.getPassword());
            if (result.isAuthenticated()) {
                return userRepository.findById(result.userId())
                        .orElseThrow(() -> new RuntimeException("Invalid username or password"));
            }
            if (!result.shouldFallbackToLocal()) {
                if (shouldFallbackToLocalAccount(request.getUsername())) {
                    log.info("LDAP rejected credentials for local-managed user {}, will try local password", request.getUsername());
                    return authenticateLocal(request);
                }
                log.warn("LDAP rejected credentials for user: {}", request.getUsername());
                recordLocalFailure(request.getUsername());
                throw new RuntimeException("Invalid username or password");
            }
            log.debug("LDAP fallback to local for user: {} (outcome={})",
                    request.getUsername(), result.outcome());
        }
        return authenticateLocal(request);
    }
    private boolean shouldFallbackToLocalAccount(String username) {
        Optional<User> local = userRepository.findByUsername(username);
        return local.isPresent()
                && !LdapConstants.LDAP_SYNC_ACTOR.equals(local.get().getCreatedBy());
    }

    /** 本地账号密码认证（含失败计数与连续失败锁定）。 */
    private User authenticateLocal(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("User not found: {}", request.getUsername());
                    return new RuntimeException("Invalid username or password");
                });

        assertAccountActive(user, request.getUsername());

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
        return user;
    }

    /** LDAP 拒绝时，对已存在的本地用户记一次失败计数（保留暴力破解防护），用户不存在则忽略。 */
    private void recordLocalFailure(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.incrementFailedLoginCount();
            if (user.getFailedLoginCount() >= 5) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
            }
            userRepository.save(user);
        });
    }

    /** 账号状态拦截：LOCKED / INACTIVE 直接拒绝。 */
    private void assertAccountActive(User user, String username) {
        if (user.getStatus() == UserStatus.LOCKED) {
            log.warn("Account locked: {}", username);
            throw new RuntimeException("Account is locked");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            log.warn("Account disabled: {}", username);
            throw new RuntimeException("Account is disabled");
        }
    }

    /**
     * Admin Center 访问校验：需具备 SYS_ADMIN 或 AUDITOR。
     *
     * <p>{@code admin.auth.developer-bypass-role-check=true} 时跳过（仅本地联调，默认关闭）。</p>
     */
    private void ensureAdminAccess(User user, List<String> userRoleCodes) {
        if (adminAuthProperties.isDeveloperBypassRoleCheck()) {
            log.warn("DEV BYPASS enabled: skipping Admin Center role check for user {} (admin.auth.developer-bypass-role-check=true)",
                    user.getUsername());
            return;
        }
        boolean hasAdminAccess = userRoleCodes.stream()
                .anyMatch(code -> "SYS_ADMIN".equals(code) || "AUDITOR".equals(code));
        if (!hasAdminAccess) {
            log.warn("User {} does not have admin center access. Roles: {}", user.getUsername(), userRoleCodes);
            throw new RuntimeException("You do not have access to Admin Center");
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

    private String resolveDisplayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isEmpty()) {
            return user.getFullName();
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }
        return user.getUsername();
    }

    private List<String> getPermissionsForRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of("basic:access");
        }
        
        List<String> permissions = new ArrayList<>();
        
        for (String roleCode : roles) {
            try {
                List<String> rolePermissions = jdbcTemplate.queryForList(
                    "SELECT p.code FROM sys_permissions p " +
                    "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
                    "JOIN sys_roles r ON rp.role_id = r.id " +
                    "WHERE r.code = ? AND r.status = 'ACTIVE'",
                    String.class,
                    roleCode
                );
                
                if (!rolePermissions.isEmpty()) {
                    log.debug("Found {} permissions for role {} from database", rolePermissions.size(), roleCode);
                    permissions.addAll(rolePermissions);
                }
            } catch (Exception e) {
                log.warn("Failed to get permissions for role {}: {}", roleCode, e.getMessage());
            }
        }
        
        if (permissions.isEmpty()) {
            permissions.add("basic:access");
        }
        
        return permissions.stream().distinct().toList();
    }
    
    private Map<String, String> getRoleNames(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Map.of();
        }
        
        try {
            String placeholders = roleCodes.stream()
                    .map(code -> "?")
                    .collect(Collectors.joining(","));
            
            String sql = "SELECT code, name FROM sys_roles WHERE code IN (" + placeholders + ")";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, roleCodes.toArray());
            
            return results.stream()
                    .collect(Collectors.toMap(
                            row -> (String) row.get("code"),
                            row -> (String) row.get("name")
                    ));
        } catch (Exception e) {
            log.warn("Failed to get role names: {}", e.getMessage());
            return Map.of();
        }
    }

    private void setAuthCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);  // false for dev HTTP
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
